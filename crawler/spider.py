
import re
import time
import random
import jieba
import json
import os
import glob
import tempfile
import sys
import argparse
from datetime import datetime
from urllib.parse import quote

import requests
import psycopg2
from DrissionPage import ChromiumPage, ChromiumOptions
from DrissionPage.errors import BrowserConnectError

CONFIG_FILE = os.path.join(os.path.dirname(__file__), 'config.json')
RUNTIME_CONFIG_FILE = os.environ.get("JOBDATA_RUNTIME_CONFIG") or os.path.join(os.path.dirname(__file__), "runtime_config.json")

API_51JOB_BASE = "https://we.51job.com/api/job/search-pc"

DEFAULT_CITY_CODES_51JOB = {
    "北京": "010000",
    "上海": "020000",
    "广州": "030200",
    "深圳": "040000",
    "杭州": "080200",
    "苏州": "070300",
    "南京": "070200",
    "成都": "090200",
    "武汉": "180200",
    "西安": "200200",
    "重庆": "060000",
    "天津": "050000",
    "郑州": "170200",
    "长沙": "190200",
    "青岛": "120200",
    "大连": "230200",
    "厦门": "110200",
    "宁波": "080300",
    "无锡": "070400",
    "合肥": "150200",
    "福州": "110300"
}

def find_browser_path(preferred=None):
    pref = str(preferred or "auto").strip().lower()
    rc = load_runtime_config()
    b = rc.get("browser") or {}
    edge_candidates = list(b.get("edge_candidates") or [])
    chrome_candidates = list(b.get("chrome_candidates") or [])

    local_app_data = os.environ.get("LOCALAPPDATA") or ""
    if local_app_data:
        edge_rel = str(b.get("local_appdata_edge_relative") or "").strip()
        edge_user = os.path.join(local_app_data, edge_rel) if edge_rel else ""
        if os.path.exists(edge_user):
            edge_candidates.insert(0, edge_user)

        pw_glob = str(b.get("local_appdata_playwright_glob") or "").strip()
        if pw_glob:
            for p in glob.glob(os.path.join(local_app_data, pw_glob)):
                chrome_candidates.insert(0, p)

    if pref in ("edge", "msedge"):
        candidates = edge_candidates + chrome_candidates
    elif pref in ("chrome", "google-chrome"):
        candidates = chrome_candidates + edge_candidates
    else:
        candidates = edge_candidates + chrome_candidates

    for p in candidates:
        if p and os.path.exists(p):
            return p
    return None

_runtime_config_cache = None

def load_runtime_config():
    global _runtime_config_cache
    if isinstance(_runtime_config_cache, dict):
        return _runtime_config_cache
    if not os.path.exists(RUNTIME_CONFIG_FILE):
        raise RuntimeError(f"找不到配置文件: {RUNTIME_CONFIG_FILE}")
    with open(RUNTIME_CONFIG_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise RuntimeError("配置文件格式错误: 需要 JSON Object")
    if not isinstance(data.get("db"), dict):
        raise RuntimeError("配置文件缺少 db 配置")
    _runtime_config_cache = data
    return _runtime_config_cache

def get_db_config():
    rc = load_runtime_config()
    db = rc.get("db") or {}
    return {
        "type": (db.get("type") or "postgres"),
        "host": db.get("host"),
        "port": int(db.get("port") or 0),
        "user": db.get("user"),
        "password": db.get("password"),
        "database": db.get("database"),
    }

def get_user_agent_of_pc():
    user_agents = [
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36',
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0'
    ]
    return random.choice(user_agents)

def create_session_from_page(page, keyword, city_code):
    session = requests.Session()
    sync_session_cookies(session, page)

    session.headers.update({
        'User-Agent': get_user_agent_of_pc(),
        'Accept': 'application/json, text/plain, */*',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
        'Origin': 'https://www.zhipin.com',
        'Referer': f'https://www.zhipin.com/web/geek/job?query={quote(keyword)}&city={city_code}',
        'Accept-Language': 'zh-CN,zh;q=0.9',
        'Connection': 'close'
    })

    return session

def sync_session_cookies(session, page):
    cookies_dict = {}
    try:
        cookies_list = page.cookies()
        for cookie in cookies_list:
            name = cookie.get('name')
            value = cookie.get('value')
            if name and value is not None:
                cookies_dict[name] = value
    except Exception:
        cookies_dict = {}

    session.cookies.clear()
    for k, v in cookies_dict.items():
        session.cookies.set(k, v)

def fetch_job_detail_desc(session, page, security_id, referer=None):
    if not security_id:
        return None

    url = 'https://www.zhipin.com/wapi/zpgeek/job/detail.json'
    try:
        sync_session_cookies(session, page)
        if referer:
            session.headers['Referer'] = referer
        resp = session.get(url, params={'securityId': security_id}, timeout=15)
        if resp.status_code != 200:
            return None
        result = resp.json()
        if result.get('code') != 0:
            return None
        zp_data = result.get('zpData') or {}
        job_info = zp_data.get('jobInfo') or {}
        desc = job_info.get('postDescription')
        return desc.strip() if isinstance(desc, str) and desc.strip() else None
    except Exception:
        return None

def get_city_code(city_name):
    city_codes = {
        '北京': '101010100',
        '上海': '101020100',
        '广州': '101280100',
        '深圳': '101280600',
        '杭州': '101210100',
        '成都': '101270100',
        '武汉': '101200100',
        '西安': '101110100',
        '重庆': '101040100',
        '南京': '101190100',
        '苏州': '101190400',
        '天津': '101030100',
        '郑州': '101180100',
        '长沙': '101250100',
        '青岛': '101120200',
        '大连': '101070200',
        '厦门': '101230200',
        '宁波': '101210400',
        '无锡': '101190800',
        '合肥': '101220100',
        '福州': '101230100',
        '济南': '101120100',
        '昆明': '101290100',
        '南昌': '101240100',
        '哈尔滨': '101050100',
        '沈阳': '101070100',
        '长春': '101060100',
        '石家庄': '101090100',
        '太原': '101100100'
    }
    return city_codes.get(city_name, '101010100')

def load_config():
    default_config = {
        "platform": "boss",
        "keywords": ["Java", "Python", "前端", "数据分析", "产品经理"],# 关键词
        "cities": ["北京", "上海", "广州", "深圳", "杭州","福州"],# 城市
        "pages_per_keyword": 2,# 每个关键词爬取的页数
        "pages_per_city_51job": 2,
        "delay_min": 3,# 最小延迟时间（秒）
        "delay_max": 8,# 最大延迟时间（秒）
        "city_codes_51job": DEFAULT_CITY_CODES_51JOB
    }
    
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
                config = json.load(f)
                default_config.update(config)
        except Exception as e:
            print(f"读取配置文件失败，使用默认配置: {e}")
    
    return default_config

def parse_salary(salary_text):
    if not salary_text:
        return None, None
    text = str(salary_text).strip()

    match = re.search(r'(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*K', text, flags=re.IGNORECASE)
    if match:
        return int(float(match.group(1))), int(float(match.group(2)))

    match = re.search(r'(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*万/年', text)
    if match:
        low = float(match.group(1)) * 10000 / 12 / 1000
        high = float(match.group(2)) * 10000 / 12 / 1000
        return int(round(low)), int(round(high))

    match = re.search(r'(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*万', text)
    if match:
        low = float(match.group(1)) * 10
        high = float(match.group(2)) * 10
        return int(round(low)), int(round(high))

    match = re.search(r'(\d+(?:\.\d+)?)\s*千\s*-\s*(\d+(?:\.\d+)?)\s*千', text)
    if match:
        return int(round(float(match.group(1)))), int(round(float(match.group(2))))

    match = re.search(r'(\d+(?:\.\d+)?)\s*千\s*-\s*(\d+(?:\.\d+)?)\s*万', text)
    if match:
        low = float(match.group(1))
        high = float(match.group(2)) * 10
        return int(round(low)), int(round(high))

    match = re.search(r'(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*千', text)
    if match:
        return int(round(float(match.group(1)))), int(round(float(match.group(2))))

    match = re.search(r'(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*万', text)
    if match:
        low = float(match.group(1)) * 10
        high = float(match.group(2)) * 10
        return int(round(low)), int(round(high))

    match = re.search(r'(\d+(?:\.\d+)?)\s*千', text)
    if match:
        val = int(round(float(match.group(1))))
        return val, val

    match = re.search(r'(\d+(?:\.\d+)?)\s*万', text)
    if match:
        val = int(round(float(match.group(1)) * 10))
        return val, val

    return None, None

def parse_city(city_text):
    if not city_text:
        return None
    return city_text.split('·')[0].strip()

def parse_education(education_text):
    if not education_text:
        return None
    edu_map = {'博士': '博士', '硕士': '硕士', '本科': '本科', '大专': '大专', '高中': '高中'}# 学历映射
    for key in edu_map:
        if key in education_text:
            return edu_map[key]
    return education_text

def extract_keywords(text):
    if not text:
        return ''
    words = jieba.lcut(text)
    stop_words = ['的', '了', '在', '是', '我', '有', '和', '就', '不', '人', '都', '一', '一个', '上', '也', '很', '到', '说', '要', '去', '你', '会', '着', '没有', '看', '好', '自己', '这']
    keywords = [w for w in words if len(w) >= 2 and w not in stop_words]
    return ','.join(keywords[:20])

def extract_welfare_from_desc(job_desc):
    if not isinstance(job_desc, str):
        return None

    text = re.sub(r"\r\n?", "\n", job_desc).strip()
    if not text:
        return None

    if not re.search(r"福利|待遇|薪酬福利|员工福利|我们提供|公司提供", text):
        return None

    heading = re.compile(r"(?im)^(?:\s*)(福利待遇|薪酬福利|员工福利|公司福利|福利|待遇)\s*[:：]?")
    stop_heading = re.compile(
        r"(?im)^(?:\s*)(岗位职责|工作职责|职责描述|工作内容|职位描述|任职要求|岗位要求|任职资格|职位要求|职位亮点|工作地点|联系方式|公司介绍|企业介绍)\s*[:：]?"
    )

    m = heading.search(text)
    if m:
        start = m.start()
        tail = text[start:]
        m2 = stop_heading.search(tail[m.end() - start :])
        if m2:
            end = (m.end() - start) + m2.start()
            block = tail[:end]
        else:
            block = tail
        block = re.sub(r"\n{3,}", "\n\n", block).strip()
        return block if block else None

    lines = [ln.strip() for ln in text.split("\n") if ln.strip()]
    if not lines:
        return None

    welfare_terms = re.compile(
        r"五险一金|六险一金|补充医疗|商业保险|带薪年假|年终奖|绩效奖金|节日福利|定期体检|周末双休|双休|弹性工作|加班补助|餐补|交通补贴|通讯补贴|住房补贴|住房公积金|员工旅游|团建|下午茶|免费班车|股票期权|入职培训|晋升|落户"
    )
    keep = []
    for ln in lines:
        if re.search(r"福利|待遇|我们提供|公司提供", ln) or welfare_terms.search(ln):
            keep.append(ln)

    if not keep:
        return None

    merged = "；".join(dict.fromkeys(keep))
    merged = re.sub(r"[；\s]+$", "", merged).strip()
    return merged if merged else None

def get_city_code_51job(city_name, config):
    codes = (config or {}).get("city_codes_51job") or {}
    code = codes.get(city_name) if isinstance(codes, dict) else None
    if not code:
        code = DEFAULT_CITY_CODES_51JOB.get(city_name)
    return str(code).strip() if code is not None and str(code).strip() else None

def ensure_51job_cookies(page, city_code):
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
        "scene": "7"
    }

_tables_ready = False

def ensure_db_tables():
    global _tables_ready
    if _tables_ready:
        return

    cfg = get_db_config()
    conn = psycopg2.connect(
        host=cfg.get("host"),
        port=int(cfg.get("port") or 5432),
        user=cfg.get("user"),
        password=cfg.get("password"),
        dbname=cfg.get("database"),
    )
    try:
        with conn.cursor() as cursor:
            cursor.execute('CREATE EXTENSION IF NOT EXISTS "uuid-ossp";')
            cursor.execute("""
            CREATE TABLE IF NOT EXISTS job_info (
                id BIGSERIAL PRIMARY KEY,
                job_name VARCHAR(255),
                company_name VARCHAR(255),
                city VARCHAR(100),
                job_url TEXT,
                salary_min INTEGER,
                salary_max INTEGER,
                salary_avg NUMERIC(10,2),
                experience VARCHAR(100),
                education VARCHAR(100),
                job_desc TEXT,
                job_keywords TEXT,
                company_size VARCHAR(100),
                company_industry VARCHAR(255),
                company_welfare TEXT,
                publish_date DATE,
                created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
            );
            """)

            cursor.execute("""
            CREATE TABLE IF NOT EXISTS job_info_51job (
                id BIGSERIAL PRIMARY KEY,
                job_name VARCHAR(255),
                company_name VARCHAR(255),
                city VARCHAR(100),
                job_url TEXT,
                salary_min INTEGER,
                salary_max INTEGER,
                salary_avg NUMERIC(10,2),
                experience VARCHAR(100),
                education VARCHAR(100),
                job_desc TEXT,
                job_keywords TEXT,
                company_size VARCHAR(100),
                company_industry VARCHAR(255),
                company_welfare TEXT,
                publish_date DATE,
                created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
            );
            """)
        conn.commit()
        _tables_ready = True
    except Exception as e:
        print(f"创建数据表失败: {e}")
        raise
    finally:
        conn.close()

def _insert_job(table_name, job_data):
    ensure_db_tables()
    cfg = get_db_config()
    conn = psycopg2.connect(
        host=cfg.get("host"),
        port=int(cfg.get("port") or 5432),
        user=cfg.get("user"),
        password=cfg.get("password"),
        dbname=cfg.get("database"),
    )
    try:
        with conn.cursor() as cursor:
            sql = f'''
            INSERT INTO {table_name} 
            (job_name, company_name, city, job_url, salary_min, salary_max, salary_avg, 
             experience, education, job_desc, job_keywords, company_size, company_industry, company_welfare, publish_date)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            '''
            salary_avg = None
            if job_data['salary_min'] and job_data['salary_max']:
                salary_avg = round((job_data['salary_min'] + job_data['salary_max']) / 2, 2)

            publish_date = job_data.get("publish_date") or datetime.now().date()
            
            cursor.execute(sql, (
                job_data['job_name'], # 岗位名称
                job_data['company_name'], # 公司名称
                job_data['city'], # 城市
                job_data.get('job_url'),
                job_data['salary_min'],# 薪资下限
                job_data['salary_max'],# 薪资上限
                salary_avg, # 薪资平均值
                job_data['experience'], # 工作经验
                job_data['education'], # 学历
                job_data.get('job_desc'), # 工作介绍
                job_data['job_keywords'], # 关键词
                job_data['company_size'], # 公司规模
                job_data['company_industry'], # 公司行业
                job_data.get('company_welfare'), # 公司福利
                publish_date # 发布日期
            ))
        conn.commit()
        return True
    except Exception as e:
        print(f"插入失败: {e}")
        conn.rollback()
        return False
    finally:
        conn.close()

def insert_job_boss(job_data):
    return _insert_job("job_info", job_data)

def insert_job_51job(job_data):
    return _insert_job("job_info_51job", job_data)

def random_delay(min_sec, max_sec):
    delay = random.uniform(min_sec, max_sec)
    print(f"等待 {delay:.1f} 秒...")
    time.sleep(delay)

def scrape_jobs(platform_override=None, browser_override=None):
    config = load_config()
    platform = str(platform_override or config.get("platform", "boss")).strip().lower()
    browser = str(browser_override or config.get("browser", "auto")).strip().lower()
    if sys.stdin.isatty() and not platform_override:
        selected = input(f"请选择数据源(boss/51job/both) [默认 {platform}]: ").strip().lower()
        if selected in ("boss", "51job", "both"):
            platform = selected

    if sys.stdin.isatty() and not browser_override:
        selected_browser = input(f"请选择浏览器(auto/edge/chrome) [默认 {browser}]: ").strip().lower()
        if selected_browser in ("auto", "edge", "chrome"):
            browser = selected_browser

    KEYWORDS = config.get("keywords", ["Java", "Python", "前端", "数据分析", "产品经理"])
    CITIES = config.get("cities", ["北京", "上海", "广州", "深圳", "杭州"])
    PAGES_PER_KEYWORD = int(config.get("pages_per_keyword", 2))
    PAGES_PER_CITY_51JOB = int(config.get("pages_per_city_51job", 2))
    DELAY_MIN = int(config.get("delay_min", 3))
    DELAY_MAX = int(config.get("delay_max", 8))

    total_inserted = 0

    print()
    print("=" * 60)
    print("招聘数据爬虫 (DrissionPage版)")
    print("=" * 60)
    print()

    try:
        ensure_mysql_tables()
        print("已确保数据表存在: job_info / job_info_51job")
    except Exception:
        return

    print("配置:")
    print(f"  数据源: {platform}")
    print(f"  关键词: {', '.join(KEYWORDS)}")
    print(f"  城市: {', '.join(CITIES)}")
    print(f"  Boss每关键词页数: {PAGES_PER_KEYWORD}")
    print(f"  51job每城市页数: {PAGES_PER_CITY_51JOB}")
    print(f"  延迟范围: {DELAY_MIN}-{DELAY_MAX} 秒")
    print(f"  浏览器: {browser}")
    print()
    print("正在启动浏览器...")

    browser_path = find_browser_path(browser)
    if not browser_path:
        print("未找到可用的浏览器可执行文件(Edge/Chrome/Chromium)。")
        return

    tmp_path = os.path.join(os.path.dirname(__file__), "tmp")
    os.makedirs(tmp_path, exist_ok=True)

    options = ChromiumOptions(read_file=False)
    options.set_browser_path(browser_path)
    options.auto_port(tmp_path=tmp_path)
    try:
        page = ChromiumPage(addr_or_opts=options)
    except BrowserConnectError as e:
        print(str(e).strip())
        print()
        print("尝试连接已启动的调试浏览器：127.0.0.1:9222")
        options2 = ChromiumOptions(read_file=False)
        options2.set_browser_path(browser_path)
        options2.set_local_port(9222)
        options2.existing_only(True)
        try:
            page = ChromiumPage(addr_or_opts=options2)
        except BrowserConnectError:
            print()
            print("当前环境无法启动/连接 Chrome 调试端口，爬虫需要可用的远程调试浏览器。")
            print("你可以在本机手动启动浏览器（新用户目录，避免和已打开的浏览器冲突）：")
            print(f"\"{browser_path}\" --remote-debugging-port=9222 --user-data-dir=$env:TEMP\\dp_boss --disable-gpu about:blank")
            print()
            print("启动后再运行 python spider.py。")
            return

    try:
        if platform in ("boss", "both"):
            print()
            print("浏览器已启动，正在打开 BOSS 直聘...")
            page.get("https://www.zhipin.com")
            time.sleep(2)

            print()
            print("请确认浏览器中是否已登录 BOSS 直聘？")
            print("如果没有登录，请先手动登录，然后按回车键继续...")
            input()

            print()
            print("开始爬取 BOSS 直聘...")
            print()

            session = None
            print("正在启动网络监听...")
            page.listen.start("joblist")

            for keyword in KEYWORDS:
                for city in CITIES:
                    print()
                    print("=" * 60)
                    print(f"正在爬取(BOSS): 关键词={keyword}, 城市={city}")
                    print("=" * 60)

                    city_code = get_city_code(city)
                    session = create_session_from_page(page, keyword, city_code)

                    for page_num in range(1, PAGES_PER_KEYWORD + 1):
                        try:
                            url = f"https://www.zhipin.com/web/geek/job?query={keyword}&city={city_code}&page={page_num}"
                            print(f"\n正在访问第 {page_num} 页: {url}")

                            page.get(url)
                            random_delay(DELAY_MIN, DELAY_MAX)

                            packet = None
                            try:
                                packet = page.listen.wait(timeout=10)
                            except Exception:
                                print("等待数据包超时，尝试继续...")

                            job_list = []
                            if packet and packet.response:
                                try:
                                    data = packet.response.body
                                    json_data = json.loads(data) if isinstance(data, str) else data
                                    if json_data.get("code") == 0:
                                        zp_data = json_data.get("zpData", {})
                                        job_list = zp_data.get("jobList", [])
                                except Exception as ex:
                                    print(f"解析响应失败: {ex}")

                            print(f"找到 {len(job_list)} 个岗位")

                            for job_item in job_list:
                                try:
                                    job_name = job_item.get("jobName", "")
                                    company_name = job_item.get("brandName", "") or job_item.get("companyName", "")
                                    salary_text = job_item.get("salaryDesc", "")
                                    city_text = job_item.get("cityName", "")
                                    experience = job_item.get("jobExperience", "")
                                    education = job_item.get("jobDegree", "")

                                    company_size = (
                                        job_item.get("brandScaleName", "")
                                        or job_item.get("brandScale", "")
                                        or job_item.get("companySize", "")
                                        or job_item.get("scale", "")
                                    )
                                    company_industry = (
                                        job_item.get("brandIndustry", "")
                                        or job_item.get("companyIndustry", "")
                                        or job_item.get("industry", "")
                                    )

                                    welfare = job_item.get("welfareList") or job_item.get("welfare") or job_item.get("welfareName")
                                    if isinstance(welfare, list):
                                        welfare = ",".join([str(x).strip() for x in welfare if str(x).strip()])
                                    elif welfare is not None:
                                        welfare = str(welfare).strip() or None

                                    security_id = job_item.get("securityId", "")
                                    encrypt_job_id = job_item.get("encryptJobId", "")
                                    referer = f"https://www.zhipin.com/job_detail/{encrypt_job_id}.html" if encrypt_job_id else None
                                    detail_desc = fetch_job_detail_desc(session, page, security_id, referer) if session else None
                                    job_desc = detail_desc or job_item.get("jobDesc", "") or job_item.get("jobDetail", "") or job_name

                                    salary_min, salary_max = parse_salary(salary_text)
                                    clean_city = parse_city(city_text)
                                    clean_education = parse_education(education)
                                    skills = job_item.get("skills")
                                    if isinstance(skills, list):
                                        keywords = ",".join([str(x).strip() for x in skills if str(x).strip()])
                                    elif isinstance(skills, str) and skills.strip():
                                        keywords = skills.strip()
                                    else:
                                        keywords = extract_keywords(job_desc)

                                    job_data = {
                                        "job_name": job_name,
                                        "company_name": company_name,
                                        "city": clean_city,
                                        "job_url": referer,
                                        "salary_min": salary_min,
                                        "salary_max": salary_max,
                                        "experience": experience,
                                        "education": clean_education,
                                        "job_desc": job_desc,
                                        "job_keywords": keywords,
                                        "company_size": company_size,
                                        "company_industry": company_industry,
                                        "company_welfare": welfare,
                                    }

                                    if job_name and company_name:
                                        if insert_job_boss(job_data):
                                            print(f"OK {job_name} @ {company_name}")
                                            total_inserted += 1
                                except Exception as ex:
                                    print(f"提取失败: {ex}")
                                    continue

                        except Exception as ex:
                            print(f"[WARN] 发生错误: {ex}")
                            continue

                        random_delay(DELAY_MIN, DELAY_MAX)

        if platform in ("51job", "qcwy", "51", "both"):
            print()
            print("开始爬取 前程无忧(51job)...")
            print()

            for keyword in KEYWORDS:
                for city in CITIES:
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

                    s.headers.update({
                        "User-Agent": get_user_agent_of_pc(),
                        "Accept": "application/json, text/plain, */*",
                        "Referer": "https://we.51job.com/pc/search",
                        "Origin": "https://we.51job.com",
                        "Connection": "close",
                    })

                    for page_num in range(1, PAGES_PER_CITY_51JOB + 1):
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

                            items = (
                                (data.get("resultbody") or {})
                                .get("job", {})
                                .get("items", [])
                            )

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

                                    company_size = (
                                        (item.get("companySizeString") or item.get("companySize") or "").strip()
                                    )
                                    company_industry = (
                                        (item.get("industryType1Str") or item.get("industryType2Str") or item.get("companyIndustry") or "").strip()
                                    )

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

                                    job_url = (
                                        item.get("jobHref")
                                        or item.get("jobUrl")
                                        or item.get("jobLink")
                                        or item.get("jobLinkUrl")
                                    )
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
                                            total_inserted += 1
                                except Exception as ex:
                                    print(f"提取失败: {ex}")
                                    continue

                        except Exception as ex:
                            print(f"[WARN] 发生错误: {ex}")
                            break

                        random_delay(DELAY_MIN, DELAY_MAX)

        print()
        print("=" * 60)
        print(f"爬取完成！共插入 {total_inserted} 条数据")
        print("=" * 60)
        print()
        if sys.stdin.isatty():
            print("按回车键退出...")
            input()
    finally:
        try:
            page.quit()
        except Exception:
            pass

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", choices=["boss", "51job", "both"])
    parser.add_argument("--browser", choices=["auto", "edge", "chrome"])
    args = parser.parse_args()
    scrape_jobs(platform_override=args.platform, browser_override=args.browser)
