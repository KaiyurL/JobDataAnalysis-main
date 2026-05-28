"""
前程无忧(51job)爬虫（接口采集版）

特点：
- 通过 DrissionPage 打开 51job 搜索页，人工通过验证后同步 cookies
- 使用 requests 访问 51job 搜索接口 API_51JOB_BASE 获取 JSON 列表数据
- 解析岗位信息后入库到 job_info_51job 表

入口：
- run_51job(page, config, keywords, cities, pages_per_city, delay_min, delay_max) -> inserted_count
"""

import time
from datetime import datetime

import requests

from .spider_common import (
    API_51JOB_BASE,
    DEFAULT_CITY_CODES_51JOB,
    get_user_agent_of_pc,
    parse_salary,
    parse_education,
    extract_welfare_from_desc,
    insert_job_51job,
    random_delay,
)


def get_city_code_51job(city_name, config):
    """
    获取 51job 城市编码。

    优先读取 config.city_codes_51job，其次使用 DEFAULT_CITY_CODES_51JOB。
    """
    codes = (config or {}).get("city_codes_51job") or {}
    code = codes.get(city_name) if isinstance(codes, dict) else None
    if not code:
        code = DEFAULT_CITY_CODES_51JOB.get(city_name)
    return str(code).strip() if code is not None and str(code).strip() else None


def ensure_51job_cookies(page, city_code):
    """
    打开 51job 搜索页并确保 cookies 可用。

    站点可能触发验证：若列表未加载出来，会提示用户在浏览器完成验证后回车继续。
    返回 cookies dict（name->value），失败返回 None。
    """
    url = (
        f"https://we.51job.com/pc/search?keyword=&keywordType=2"
        f"&jobArea={city_code}&issuedDate=4&pageNum=1&pageSize=20"
    )
    page.get(url)
    time.sleep(3)

    ok = False
    for _ in range(15):
        try:
            cnt = page.run_js("return document.querySelectorAll('.joblist-item').length")
        except Exception:
            cnt = 0
        if isinstance(cnt, int) and cnt >= 5:
            ok = True
            break
        time.sleep(1)

    if not ok:
        print()
        print("请在浏览器中完成前程无忧验证/等待列表加载，然后按回车键继续...")
        input()

    cookies_list = []
    try:
        cookies_list = page.cookies()
    except Exception:
        cookies_list = []

    cookies = {}
    for c in cookies_list or []:
        name = c.get("name")
        value = c.get("value")
        if name and value is not None:
            cookies[name] = value
    return cookies if cookies else None


def build_51job_params(keyword, city_code, page_num, page_size=20):
    """
    构造 51job 搜索接口参数。

    参数来自 51job PC 端接口请求，包含 timestamp/pageNum/pageSize 等字段。
    """
    ts = int(time.time() * 1000)
    return {
        "api_key": "51job",
        "timestamp": ts,
        "keyword": keyword or "",
        "searchType": "2",
        "function": "",
        "industry": "",
        "jobArea": city_code,
        "jobArea2": "",
        "landmark": "",
        "metro": "",
        "salary": "",
        "workYear": "",
        "degree": "",
        "companyType": "",
        "companySize": "",
        "jobType": "",
        "issueDate": "4",
        "sortType": "0",
        "pageNum": int(page_num),
        "requestId": "",
        "keywordType": "2",
        "pageSize": str(page_size),
        "source": "1",
        "accountId": "",
        "pageCode": "sou|sou|soulb",
        "scene": "7",
    }


def run_51job(page, config, keywords, cities, pages_per_city, delay_min, delay_max):
    """
    执行 51job 采集。

    流程：
    - 用 DrissionPage 打开搜索页获取 cookies
    - requests 调用 JSON 搜索接口分页采集
    - 解析并入库（job_info_51job）
    返回新增插入条数。
    """
    inserted = 0
    print()
    print("开始爬取 前程无忧(51job)...")
    print()

    for keyword in keywords:
        for city in cities:
            city_code_51job = get_city_code_51job(city, config)
            if not city_code_51job:
                print(f"跳过城市(51job): {city} (缺少 city_codes_51job 映射)")
                continue

            print()
            print("=" * 60)
            print(f"正在爬取(51job): 关键词={keyword}, 城市={city}, code={city_code_51job}")
            print("=" * 60)

            cookies = ensure_51job_cookies(page, city_code_51job)
            if not cookies:
                print("获取 cookies 失败，跳过")
                continue

            s = requests.Session()
            for name, value in cookies.items():
                s.cookies.set(name, value, domain=".51job.com", path="/")

            s.headers.update(
                {
                    "User-Agent": get_user_agent_of_pc(),
                    "Accept": "application/json, text/plain, */*",
                    "Referer": "https://we.51job.com/pc/search",
                    "Origin": "https://we.51job.com",
                    "Connection": "close",
                }
            )

            for page_num in range(1, pages_per_city + 1):
                try:
                    params = build_51job_params(keyword, city_code_51job, page_num, page_size=20)
                    resp = s.get(API_51JOB_BASE, params=params, timeout=15)

                    ct = resp.headers.get("content-type", "")
                    if "text/html" in ct or resp.text.lstrip().startswith("<"):
                        cookies2 = ensure_51job_cookies(page, city_code_51job)
                        if not cookies2:
                            print("WAF 拦截，cookie 获取失败，跳过剩余页")
                            break
                        s.cookies.clear()
                        for name, value in cookies2.items():
                            s.cookies.set(name, value, domain=".51job.com", path="/")
                        resp = s.get(API_51JOB_BASE, params=params, timeout=15)

                    try:
                        data = resp.json()
                    except Exception:
                        print("解析响应失败，跳过剩余页")
                        break

                    items = ((data.get("resultbody") or {}).get("job", {}) or {}).get("items", [])

                    print(f"找到 {len(items)} 个岗位")
                    if not items:
                        break

                    for item in items:
                        try:
                            job_name = (item.get("jobName") or "").strip()
                            company_name = (item.get("companyName") or "").strip()
                            salary_text = (item.get("provideSalaryString") or "").strip()
                            experience = (item.get("workYearString") or item.get("workYear") or "").strip()
                            education = (item.get("degreeString") or item.get("degree") or "").strip()

                            issue_date = (item.get("issueDateString") or "").strip()
                            publish_date = None
                            if issue_date:
                                date_part = issue_date.split(" ")[0].strip()
                                try:
                                    publish_date = datetime.strptime(date_part, "%Y-%m-%d").date()
                                except Exception:
                                    publish_date = None

                            company_size = (item.get("companySizeString") or item.get("companySize") or "").strip()
                            company_industry = (
                                item.get("industryType1Str")
                                or item.get("industryType2Str")
                                or item.get("companyIndustry")
                                or ""
                            ).strip()

                            welfare = item.get("jobWelfare") or item.get("jobWelf") or item.get("jobWelfareList")
                            if isinstance(welfare, list):
                                welfare = ",".join([str(x).strip() for x in welfare if str(x).strip()])
                            elif welfare is not None:
                                welfare = str(welfare).strip() or None

                            job_desc = item.get("jobDescribe") or item.get("jobDesc") or item.get("jobDetail")
                            if isinstance(job_desc, str):
                                job_desc = job_desc.strip() or None
                            else:
                                job_desc = None

                            welfare_from_desc = extract_welfare_from_desc(job_desc)
                            if (not welfare or not str(welfare).strip()) and welfare_from_desc:
                                welfare = welfare_from_desc

                            job_url = item.get("jobHref") or item.get("jobUrl") or item.get("jobLink") or item.get("jobLinkUrl")
                            if isinstance(job_url, str):
                                job_url = job_url.strip()
                                if job_url.startswith("//"):
                                    job_url = "https:" + job_url
                                elif job_url.startswith("/"):
                                    job_url = "https://jobs.51job.com" + job_url
                                job_url = job_url or None
                            else:
                                job_url = None

                            salary_min, salary_max = parse_salary(salary_text)
                            clean_education = parse_education(education)

                            job_data = {
                                "job_name": job_name,
                                "company_name": company_name,
                                "city": city,
                                "job_url": job_url,
                                "salary_min": salary_min,
                                "salary_max": salary_max,
                                "experience": experience,
                                "education": clean_education,
                                "job_desc": job_desc,
                                "job_keywords": keyword or "",
                                "company_size": company_size,
                                "company_industry": company_industry,
                                "company_welfare": welfare,
                                "publish_date": publish_date,
                            }

                            if job_name and company_name:
                                if insert_job_51job(job_data):
                                    print(f"OK {job_name} @ {company_name}")
                                    inserted += 1
                        except Exception as ex:
                            print(f"提取失败: {ex}")
                            continue

                except Exception as ex:
                    print(f"[WARN] 发生错误: {ex}")
                    break

                random_delay(delay_min, delay_max)

    return inserted
