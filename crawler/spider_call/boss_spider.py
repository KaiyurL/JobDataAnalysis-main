"""
BOSS 直聘爬虫（页面解析版）

特点：
- 使用 DrissionPage 打开列表页并滚动加载岗位卡片
- 列表页薪资可能字体加密：通过打开岗位详情页提取明文薪资与职位描述
- 以 job_url 在数据库中做增量去重（已存在则跳过详情页，提高效率）

入口：
- run_boss(page, keywords, cities, max_scrolls, delay_min, delay_max) -> inserted_count
"""

import time
import random
from urllib.parse import quote

import psycopg2

from .spider_common import (
    get_city_code,
    get_db_config,
    parse_salary,
    parse_city,
    parse_education,
    extract_keywords,
    extract_welfare_from_desc,
    insert_job_boss,
    random_delay,
)


def boss_prepare_list_page(page, keyword, city_code):
    """
    打开 BOSS 列表页并做少量滚动预热。

    预热用于触发页面资源加载，降低后续滚动加载失败概率。
    """
    q = quote(str(keyword or "").strip())
    url = f"https://www.zhipin.com/web/geek/jobs?query={q}&city={city_code}"
    page.get(url)
    time.sleep(2)
    for _ in range(3):
        page.scroll.down(random.randint(8, 16))
        time.sleep(0.6)


def boss_scroll_load_jobs(page, max_scrolls=60):
    """
    通过分段滚动加载更多岗位卡片。

    参数 max_scrolls 表示最多尝试滚动次数；当连续多次滚动后卡片数量不再增长则提前结束。
    """
    last_count = 0
    no_increase = 0
    for _ in range(int(max_scrolls or 0)):
        try:
            job_list = page.ele('xpath://ul[contains(@class, "rec-job-list")]', timeout=5) or page.ele(".rec-job-list", timeout=5)
        except Exception:
            job_list = None
        if not job_list:
            time.sleep(1)
            continue
        try:
            cards = job_list.eles('xpath:.//li[contains(@class, "job-card-box")]') or job_list.eles(".job-card-box")
        except Exception:
            cards = []
        current = len(cards or [])
        if current > last_count:
            last_count = current
            no_increase = 0
        else:
            no_increase += 1
            if no_increase >= 8:
                break
        try:
            page.scroll.down(random.randint(500, 1000))
        except Exception:
            try:
                page.scroll.down(1000)
            except Exception:
                pass
        if random.random() < 0.15:
            try:
                page.scroll.up(random.randint(50, 100))
            except Exception:
                pass
        time.sleep(random.uniform(1.2, 2))


def boss_parse_job_cards(page):
    """
    从列表页解析岗位卡片。

    返回列表元素结构：
    - job_name/company_name/location/experience/education/job_url
    """
    try:
        job_list = page.ele('xpath://ul[contains(@class, "rec-job-list")]', timeout=5) or page.ele(".rec-job-list", timeout=5)
    except Exception:
        job_list = None
    if not job_list:
        return []
    try:
        cards = job_list.eles('xpath:.//li[contains(@class, "job-card-box")]') or job_list.eles(".job-card-box")
    except Exception:
        cards = []

    jobs = []
    for card in cards or []:
        try:
            name_elem = card.ele('xpath:.//a[contains(@class, "job-name")]', timeout=0.5) or card.ele(".job-name", timeout=0.5)
            if not name_elem:
                continue
            job_name = str(name_elem.text or "").strip()
            href = name_elem.attr("href") or ""
            job_url = href if href.startswith("http") else (f"https://www.zhipin.com{href}" if href.startswith("/") else "")
            if not job_name or not job_url:
                continue

            company_elem = card.ele('xpath:.//span[contains(@class, "boss-name")]', timeout=0.5) or card.ele(".boss-name", timeout=0.5)
            company_name = str(company_elem.text or "").strip() if company_elem else ""

            loc_elem = card.ele('xpath:.//span[contains(@class, "company-location")]', timeout=0.5) or card.ele(".company-location", timeout=0.5)
            loc_text = str(loc_elem.text or "").strip() if loc_elem else ""

            exp = ""
            edu = ""
            try:
                tag_list = card.ele('xpath:.//ul[contains(@class, "tag-list")]', timeout=0.5) or card.ele(".tag-list", timeout=0.5)
                if tag_list:
                    tags = [str(x.text or "").strip() for x in (tag_list.eles("tag:li") or [])]
                    if len(tags) > 0:
                        exp = tags[0]
                    if len(tags) > 1:
                        edu = tags[1]
            except Exception:
                pass

            jobs.append(
                {
                    "job_name": job_name,
                    "company_name": company_name,
                    "location": loc_text,
                    "experience": exp,
                    "education": edu,
                    "job_url": job_url,
                }
            )
        except Exception:
            continue
    return jobs


def boss_parse_detail_in_new_tab(page, job_url):
    """
    打开岗位详情页提取薪资与职位描述。

    返回 (salary_text, job_desc_text, company_size, company_industry, company_welfare, job_keyword_tags)。
    失败返回 (None, None, None, None, None, None)。
    """
    try:
        tab = page.new_tab(job_url, background=False)
    except Exception:
        try:
            page.get(job_url)
            tab = page
        except Exception:
            return None, None, None, None, None, None

    try:
        time.sleep(2)
        salary = None
        for sel in (
            'xpath://span[contains(@class, "salary")]',
            'xpath://span[contains(@class, "job-salary")]',
            'xpath://span[contains(@class, "salary-text")]',
        ):
            try:
                ele = tab.ele(sel, timeout=2)
                if ele and str(ele.text or "").strip():
                    salary = str(ele.text or "").strip()
                    break
            except Exception:
                continue

        desc = None
        for sel in (
            'xpath://div[contains(@class, "job-sec-text")]',
            'xpath://div[contains(@class, "job-detail-section")]//div[contains(@class, "text")]',
            'xpath://div[contains(@class, "job-detail")]',
        ):
            try:
                ele = tab.ele(sel, timeout=2)
                text = str(ele.text or "").strip() if ele else ""
                if text:
                    desc = text
                    break
            except Exception:
                continue
        company_size = None
        company_industry = None
        company_welfare = None
        job_keyword_tags = None

        kw_items = []
        for sel in (
            'xpath://ul[contains(@class, "job-keyword-list")]/li',
            "css:ul.job-keyword-list > li",
        ):
            try:
                eles = tab.eles(sel, timeout=1)
            except Exception:
                eles = []
            for e in eles or []:
                try:
                    t = str(e.text or "").replace("\n", "").strip()
                except Exception:
                    t = ""
                if not t:
                    continue
                t = t.replace("BOSS直聘", "").strip()
                if not t:
                    continue
                if t not in kw_items:
                    kw_items.append(t)
        if kw_items:
            job_keyword_tags = kw_items

        tags = []
        for sel in (
            'xpath://div[contains(@class, "job-company")]//*[self::span or self::a][contains(@class, "company") or contains(@class, "text") or contains(@class, "tag") or contains(@class, "info")]',
            'xpath://div[contains(@class, "company-info")]//*[self::span or self::a or self::p]',
            'xpath://div[contains(@class, "sider-company")]//*[self::span or self::a or self::p]',
        ):
            try:
                eles = tab.eles(sel, timeout=1)
            except Exception:
                eles = []
            for e in eles or []:
                try:
                    t = str(e.text or "").strip()
                except Exception:
                    t = ""
                if t and t not in tags:
                    tags.append(t)

        if tags:
            for t in tags:
                if "人" in t and (t.endswith("人") or t.endswith("人以上") or t.endswith("以上")):
                    company_size = company_size or t

            for t in tags:
                if not t:
                    continue
                if company_size and t == company_size:
                    continue
                if "融资" in t or "上市" in t or "未融资" in t:
                    continue
                if "人" in t:
                    continue
                if len(t) > 20:
                    continue
                company_industry = company_industry or t

        welfare_tags = []
        for sel in (
            'xpath://*[(self::div or self::section) and (contains(., "职位福利") or contains(., "福利"))]//span[contains(@class, "tag") or contains(@class, "item") or contains(@class, "text")]',
            'xpath://div[contains(@class, "job-tags")]//span',
            'xpath://div[contains(@class, "job-welfare")]//span',
        ):
            try:
                eles = tab.eles(sel, timeout=1)
            except Exception:
                eles = []
            for e in eles or []:
                try:
                    t = str(e.text or "").strip()
                except Exception:
                    t = ""
                if t and t not in welfare_tags:
                    welfare_tags.append(t)

        if welfare_tags:
            company_welfare = ",".join(welfare_tags[:30])
        else:
            company_welfare = extract_welfare_from_desc(desc)

        return salary, desc, company_size, company_industry, company_welfare, job_keyword_tags
    finally:
        if tab is not page:
            try:
                tab.close()
            except Exception:
                pass


def run_boss(page, keywords, cities, max_scrolls, delay_min, delay_max):
    """
    执行 BOSS 采集。

    流程：
    - 打开列表页并滚动加载卡片
    - 解析卡片得到 job_url
    - 以 job_url 做增量去重；仅对新岗位打开详情页提取信息
    - 入库到 job_info
    返回新增插入条数。
    """
    inserted = 0
    for keyword in keywords:
        for city in cities:
            print()
            print("=" * 60)
            print(f"正在爬取(BOSS): 关键词={keyword}, 城市={city}")
            print("=" * 60)

            city_code = get_city_code(city)
            boss_prepare_list_page(page, keyword, city_code)

            boss_scroll_load_jobs(page, max_scrolls=max(max_scrolls, 30))
            jobs = boss_parse_job_cards(page)
            print(f"共解析到 {len(jobs)} 个岗位卡片")

            cfg = get_db_config()
            dup_conn = psycopg2.connect(
                host=cfg.get("host"),
                port=int(cfg.get("port") or 5432),
                user=cfg.get("user"),
                password=cfg.get("password"),
                dbname=cfg.get("database"),
            )
            dup_cursor = dup_conn.cursor()
            dup_cache = set()

            try:
                for job_item in jobs:
                    try:
                        job_name = str(job_item.get("job_name") or "").strip()
                        company_name = str(job_item.get("company_name") or "").strip()
                        job_url = str(job_item.get("job_url") or "").strip()
                        location = str(job_item.get("location") or "").strip()
                        experience = str(job_item.get("experience") or "").strip()
                        education = str(job_item.get("education") or "").strip()

                        if job_url in dup_cache:
                            prefix = f"{job_name} @ {company_name} - " if (job_name or company_name) else ""
                            print(f"跳过重复简历: {prefix}")
                            continue
                        if job_url:
                            dup_cursor.execute("SELECT 1 FROM job_info WHERE job_url = %s LIMIT 1", (job_url,))
                            if dup_cursor.fetchone() is not None:
                                dup_cache.add(job_url)
                                prefix = f"{job_name} @ {company_name} - " if (job_name or company_name) else ""
                                print(f"跳过重复简历: {prefix}")
                                continue

                        (
                            salary_text,
                            detail_desc,
                            company_size,
                            company_industry,
                            company_welfare,
                            job_keyword_tags,
                        ) = boss_parse_detail_in_new_tab(page, job_url)
                        job_desc = detail_desc or job_name

                        salary_min, salary_max = parse_salary(salary_text or "")
                        clean_city = parse_city(location)
                        clean_education = parse_education(education)
                        if job_keyword_tags:
                            job_keywords = ",".join([str(x).strip() for x in job_keyword_tags if str(x).strip()][:30])
                        else:
                            job_keywords = None

                        job_data = {
                            "job_name": job_name,
                            "company_name": company_name,
                            "city": clean_city,
                            "job_url": job_url,
                            "salary_min": salary_min,
                            "salary_max": salary_max,
                            "experience": experience,
                            "education": clean_education,
                            "job_desc": job_desc,
                            "job_keywords": job_keywords,
                            "company_size": company_size,
                            "company_industry": company_industry,
                            "company_welfare": company_welfare,
                        }

                        if job_name and company_name:
                            if insert_job_boss(job_data):
                                print(f"OK {job_name} @ {company_name}")
                                inserted += 1
                        random_delay(max(0.5, (delay_min-1) // 2), max(1, (delay_max-1) // 2))
                    except Exception as ex:
                        print(f"提取失败: {ex}")
                        continue
            finally:
                try:
                    dup_cursor.close()
                except Exception:
                    pass
                try:
                    dup_conn.close()
                except Exception:
                    pass
    return inserted
