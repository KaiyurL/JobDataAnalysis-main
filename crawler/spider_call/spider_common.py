"""
爬虫公共模块

职责：
- 配置读取：crawler/config.json（platform/keywords/cities/pages_per_keyword/pages_per_city_51job/delay_* 等）
- 运行时配置读取：crawler/runtime_config.json（db 连接信息、浏览器候选路径）
- 浏览器路径探测：find_browser_path()
- DB：ensure_db_tables() / insert_job_boss() / insert_job_51job()
- 通用解析：parse_salary()/parse_city()/parse_education()/extract_keywords()/extract_welfare_from_desc()
"""

import re
import time
import random
import jieba
import json
import os
import glob
from datetime import datetime

import psycopg2

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
CONFIG_FILE = os.path.join(BASE_DIR, "config.json")
RUNTIME_CONFIG_FILE = os.environ.get("JOBDATA_RUNTIME_CONFIG") or os.path.join(BASE_DIR, "runtime_config.json")

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
    "福州": "110300",
}


def find_browser_path(preferred=None):
    """
    按优先级探测本机可用浏览器可执行文件路径。

    preferred:
    - auto: Edge 优先，其次 Chrome/Chromium
    - edge/chrome: 指定优先顺序
    """
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
    """
    读取 crawler/runtime_config.json（或环境变量 JOBDATA_RUNTIME_CONFIG 指定路径）。

    该配置用于提供 db 连接信息与浏览器候选路径。
    """
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
    """
    返回数据库连接配置。

    目前默认使用 PostgreSQL，字段来源 runtime_config.json 的 db。
    """
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
    """
    返回随机 PC 端 User-Agent（用于 requests 请求伪装浏览器）。
    """
    user_agents = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
    ]
    return random.choice(user_agents)

def get_city_code(city_name):
    """
    返回 BOSS 城市编码（用于列表页 query 参数）。
    未命中时默认返回北京编码。
    """
    city_codes = {
        "北京": "101010100",
        "上海": "101020100",
        "广州": "101280100",
        "深圳": "101280600",
        "杭州": "101210100",
        "成都": "101270100",
        "武汉": "101200100",
        "西安": "101110100",
        "重庆": "101040100",
        "南京": "101190100",
        "苏州": "101190400",
        "天津": "101030100",
        "郑州": "101180100",
        "长沙": "101250100",
        "青岛": "101120200",
        "大连": "101070200",
        "厦门": "101230200",
        "宁波": "101210400",
        "无锡": "101190800",
        "合肥": "101220100",
        "福州": "101230100",
        "济南": "101120100",
        "昆明": "101290100",
        "南昌": "101240100",
        "哈尔滨": "101050100",
        "沈阳": "101070100",
        "长春": "101060100",
        "石家庄": "101090100",
        "太原": "101100100",
    }
    return city_codes.get(city_name, "101010100")


def load_config():
    """
    读取 crawler/config.json 并与默认配置合并。

    该配置用于控制平台、关键词、城市、页数与延时。
    """
    default_config = {
        "platform": "boss",
        "keywords": ["Java", "Python", "前端", "数据分析", "产品经理"],
        "cities": ["北京", "上海", "广州", "深圳", "杭州", "福州"],
        "pages_per_keyword": 2,
        "pages_per_city_51job": 2,
        "delay_min": 3,
        "delay_max": 8,
        "city_codes_51job": DEFAULT_CITY_CODES_51JOB,
    }

    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                config = json.load(f)
                default_config.update(config)
        except Exception as e:
            print(f"读取配置文件失败，使用默认配置: {e}")

    return default_config


def parse_salary(salary_text):
    """
    解析常见薪资文本，返回 (salary_min_k, salary_max_k)。

    单位规则：
    - K: 直接解析（如 10-20K）
    - 万/年: 换算为月薪 K
    - 千/万: 统一换算到 K
    """
    if not salary_text:
        return None, None
    text = str(salary_text).strip()

    match = re.search(r"(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*K", text, flags=re.IGNORECASE)
    if match:
        return int(float(match.group(1))), int(float(match.group(2)))

    match = re.search(r"(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*万/年", text)
    if match:
        low = float(match.group(1)) * 10000 / 12 / 1000
        high = float(match.group(2)) * 10000 / 12 / 1000
        return int(round(low)), int(round(high))

    match = re.search(r"(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*万", text)
    if match:
        low = float(match.group(1)) * 10
        high = float(match.group(2)) * 10
        return int(round(low)), int(round(high))

    match = re.search(r"(\d+(?:\.\d+)?)\s*千\s*-\s*(\d+(?:\.\d+)?)\s*千", text)
    if match:
        return int(round(float(match.group(1)))), int(round(float(match.group(2))))

    match = re.search(r"(\d+(?:\.\d+)?)\s*千\s*-\s*(\d+(?:\.\d+)?)\s*万", text)
    if match:
        low = float(match.group(1))
        high = float(match.group(2)) * 10
        return int(round(low)), int(round(high))

    match = re.search(r"(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*千", text)
    if match:
        return int(round(float(match.group(1)))), int(round(float(match.group(2))))

    match = re.search(r"(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s*万", text)
    if match:
        low = float(match.group(1)) * 10
        high = float(match.group(2)) * 10
        return int(round(low)), int(round(high))

    match = re.search(r"(\d+(?:\.\d+)?)\s*千", text)
    if match:
        val = int(round(float(match.group(1))))
        return val, val

    match = re.search(r"(\d+(?:\.\d+)?)\s*万", text)
    if match:
        val = int(round(float(match.group(1)) * 10))
        return val, val

    return None, None


def parse_city(city_text):
    """
    解析城市字段（如“福州·仓山”取“福州”）。
    """
    if not city_text:
        return None
    return str(city_text).split("·")[0].strip()


def parse_education(education_text):
    """
    解析学历字段，提取常见学历关键词（博士/硕士/本科/大专/高中）。
    """
    if not education_text:
        return None
    edu_map = {"博士": "博士", "硕士": "硕士", "本科": "本科", "大专": "大专", "高中": "高中"}
    for key in edu_map:
        if key in str(education_text):
            return edu_map[key]
    return str(education_text)


def extract_keywords(text):
    """
    使用 jieba 对文本分词，去除停用词后返回前 20 个关键词（逗号分隔）。
    """
    if not text:
        return ""
    words = jieba.lcut(text)
    stop_words = [
        "的",
        "了",
        "在",
        "是",
        "我",
        "有",
        "和",
        "就",
        "不",
        "人",
        "都",
        "一",
        "一个",
        "上",
        "也",
        "很",
        "到",
        "说",
        "要",
        "去",
        "你",
        "会",
        "着",
        "没有",
        "看",
        "好",
        "自己",
        "这",
    ]
    keywords = [w for w in words if len(w) >= 2 and w not in stop_words]
    return ",".join(keywords[:20])


def extract_welfare_from_desc(job_desc):
    """
    从职位描述中提取福利相关文本。

    策略：
    - 优先匹配“福利/待遇”等标题块
    - 否则从多行文本中筛选包含福利关键词的句子并合并
    """
    if not isinstance(job_desc, str):
        return None

    text = re.sub(r"\r\n?", "\n", job_desc).strip()
    if not text:
        return None

    if not re.search(r"福利|待遇|薪酬福利|员工福利|我们提供|公司提供", text):
        return None

    heading = re.compile(r"(?im)^(?:\s*)(福利待遇|薪酬福利|员工福利|公司福利|福利|待遇)\s*[:：]?")
    stop_heading = re.compile(r"(?im)^(?:\s*)(岗位职责|工作职责|职责描述|工作内容|职位描述|任职要求|岗位要求|任职资格|职位要求|职位亮点|工作地点|联系方式|公司介绍|企业介绍)\s*[:：]?")

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

    welfare_terms = re.compile(r"五险一金|六险一金|补充医疗|商业保险|带薪年假|年终奖|绩效奖金|节日福利|定期体检|周末双休|双休|弹性工作|加班补助|餐补|交通补贴|通讯补贴|住房补贴|住房公积金|员工旅游|团建|下午茶|免费班车|股票期权|入职培训|晋升|落户")
    keep = []
    for ln in lines:
        if re.search(r"福利|待遇|我们提供|公司提供", ln) or welfare_terms.search(ln):
            keep.append(ln)

    if not keep:
        return None

    merged = "；".join(dict.fromkeys(keep))
    merged = re.sub(r"[；\s]+$", "", merged).strip()
    return merged if merged else None


_tables_ready = False


def ensure_db_tables():
    """
    初始化数据库表结构（若不存在则创建）。

    - job_info: BOSS 数据表
    - job_info_51job: 51job 数据表
    """
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
            cursor.execute(
                """
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
            """
            )

            cursor.execute(
                """
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
            """
            )
        conn.commit()
        _tables_ready = True
    except Exception as e:
        print(f"创建数据表失败: {e}")
        raise
    finally:
        conn.close()


def _insert_job(table_name, job_data):
    """
    向指定表插入一条岗位数据，并用 job_url 做去重。

    成功插入返回 True；重复或失败返回 False。
    """
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
            job_url = job_data.get("job_url")
            if isinstance(job_url, str):
                job_url = job_url.strip() or None

            if job_url:
                cursor.execute(f"SELECT 1 FROM {table_name} WHERE job_url = %s LIMIT 1", (job_url,))
                if cursor.fetchone() is not None:
                    job_name = str(job_data.get("job_name") or "").strip()
                    company_name = str(job_data.get("company_name") or "").strip()
                    prefix = f"{job_name} @ {company_name} - " if (job_name or company_name) else ""
                    print(f"跳过重复简历: {prefix}")
                    return False

            sql = f"""
            INSERT INTO {table_name} 
            (job_name, company_name, city, job_url, salary_min, salary_max, salary_avg, 
             experience, education, job_desc, job_keywords, company_size, company_industry, company_welfare, publish_date)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            """

            salary_avg = None
            if job_data.get("salary_min") and job_data.get("salary_max"):
                salary_avg = round((job_data["salary_min"] + job_data["salary_max"]) / 2, 2)

            publish_date = job_data.get("publish_date") or datetime.now().date()

            cursor.execute(
                sql,
                (
                    job_data.get("job_name"),
                    job_data.get("company_name"),
                    job_data.get("city"),
                    job_url,
                    job_data.get("salary_min"),
                    job_data.get("salary_max"),
                    salary_avg,
                    job_data.get("experience"),
                    job_data.get("education"),
                    job_data.get("job_desc"),
                    job_data.get("job_keywords"),
                    job_data.get("company_size"),
                    job_data.get("company_industry"),
                    job_data.get("company_welfare"),
                    publish_date,
                ),
            )
        conn.commit()
        return True
    except Exception as e:
        print(f"插入失败: {e}")
        conn.rollback()
        return False
    finally:
        conn.close()


def insert_job_boss(job_data):
    """
    写入 BOSS 岗位数据到 job_info。
    """
    return _insert_job("job_info", job_data)


def insert_job_51job(job_data):
    """
    写入 51job 岗位数据到 job_info_51job。
    """
    return _insert_job("job_info_51job", job_data)


def random_delay(min_sec, max_sec):
    """
    随机延迟，用于降低被风控概率。
    """
    delay = random.uniform(min_sec, max_sec)
    print(f"等待 {delay:.1f} 秒...")
    time.sleep(delay)
