
import re
import time
import random
import pymysql
import jieba
import json
import os
import glob
import tempfile
from datetime import datetime
from urllib.parse import quote

import requests
from DrissionPage import ChromiumPage, ChromiumOptions
from DrissionPage.errors import BrowserConnectError

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456ppoo',
    'database': 'job_data',
    'charset': 'utf8mb4'
}

CONFIG_FILE = os.path.join(os.path.dirname(__file__), 'config.json')

def find_chrome_path():
    candidates = [
        r'C:\Program Files\Google\Chrome\Application\chrome.exe',
        r'C:\Program Files (x86)\Google\Chrome\Application\chrome.exe'
    ]
    local_app_data = os.environ.get('LOCALAPPDATA') or ''
    if local_app_data:
        for p in glob.glob(os.path.join(local_app_data, 'ms-playwright', 'chromium-*', 'chrome-win', 'chrome.exe')):
            candidates.insert(0, p)

    for p in candidates:
        if p and os.path.exists(p):
            return p
    return None

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
        "keywords": ["Java", "Python", "前端", "数据分析", "产品经理"],# 关键词
        "cities": ["北京", "上海", "广州", "深圳", "杭州","福州"],# 城市
        "pages_per_keyword": 2,# 每个关键词爬取的页数
        "delay_min": 3,# 最小延迟时间（秒）
        "delay_max": 8# 最大延迟时间（秒）
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
    match = re.search(r'(\d+)-(\d+)K', salary_text)
    if match:
        return int(match.group(1)), int(match.group(2))
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

def insert_job(job_data):
    conn = pymysql.connect(**DB_CONFIG)
    try:
        with conn.cursor() as cursor:
            sql = '''
            INSERT INTO job_info 
            (job_name, company_name, city, salary_min, salary_max, salary_avg, 
             experience, education, job_desc, job_keywords, company_size, company_industry, company_welfare, publish_date)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            '''
            salary_avg = None
            if job_data['salary_min'] and job_data['salary_max']:
                salary_avg = round((job_data['salary_min'] + job_data['salary_max']) / 2, 2)
            
            cursor.execute(sql, (
                job_data['job_name'], # 岗位名称
                job_data['company_name'], # 公司名称
                job_data['city'], # 城市
                job_data['salary_min'],# 薪资下限
                job_data['salary_max'],# 薪资上限
                salary_avg, # 薪资平均值
                job_data['experience'], # 工作经验
                job_data['education'], # 学历
                job_data.get('job_desc'), # 工作介绍
                job_data['job_keywords'], # 关键词
                job_data['company_size'], # 公司规模
                job_data['company_industry'], # 公司行业
                job_data.get('company_welfare'),
                datetime.now().date() # 发布日期
            ))
        conn.commit()
        return True
    except Exception as e:
        print(f"插入失败: {e}")
        conn.rollback()
        return False
    finally:
        conn.close()

def random_delay(min_sec, max_sec):
    delay = random.uniform(min_sec, max_sec)
    print(f"等待 {delay:.1f} 秒...")
    time.sleep(delay)

def scrape_jobs():
    config = load_config()
    KEYWORDS = config.get("keywords", ["Java", "Python", "前端", "数据分析", "产品经理","数据挖掘","全栈","后端","Ai","计算机"])# 关键词
    CITIES = config.get("cities", ["北京", "上海", "广州", "深圳", "杭州","福州"])# 城市
    PAGES_PER_KEYWORD = config.get("pages_per_keyword", 2)# 每个关键词爬取的页数
    DELAY_MIN = config.get("delay_min", 3)# 最小延迟时间（秒）
    DELAY_MAX = config.get("delay_max", 8)# 最大延迟时间（秒）
    
    total_inserted = 0
    
    print()
    print("="*60)
    print("BOSS 直聘招聘数据爬虫 (DrissionPage版)")
    print("="*60)
    print()
    print(f"配置:")
    print(f"  关键词: {', '.join(KEYWORDS)}")
    print(f"  城市: {', '.join(CITIES)}")
    print(f"  每关键词页数: {PAGES_PER_KEYWORD}")
    print(f"  延迟范围: {DELAY_MIN}-{DELAY_MAX} 秒")
    print()
    print("正在启动浏览器...")
    
    chrome_path = find_chrome_path()
    if not chrome_path:
        print("未找到可用的 Chrome/Chromium 可执行文件。")
        return

    tmp_path = os.path.join(os.path.dirname(__file__), 'tmp')
    os.makedirs(tmp_path, exist_ok=True)

    options = ChromiumOptions(read_file=False)
    options.set_browser_path(chrome_path)
    options.auto_port(tmp_path=tmp_path)
    try:
        page = ChromiumPage(addr_or_opts=options)
    except BrowserConnectError as e:
        print(str(e).strip())
        print()
        print("尝试连接已启动的调试浏览器：127.0.0.1:9222")
        options2 = ChromiumOptions(read_file=False)
        options2.set_browser_path(chrome_path)
        options2.set_local_port(9222)
        options2.existing_only(True)
        try:
            page = ChromiumPage(addr_or_opts=options2)
        except BrowserConnectError:
            print()
            print("当前环境无法启动/连接 Chrome 调试端口，爬虫需要可用的远程调试浏览器。")
            print("你可以在本机手动启动 Chrome（新用户目录，避免和已打开的 Chrome 冲突）：")
            print(f"\"{chrome_path}\" --remote-debugging-port=9222 --user-data-dir=$env:TEMP\\dp_boss --disable-gpu about:blank")
            print()
            print("启动后再运行 python spider.py。")
            return
    
    print()
    print("浏览器已启动，正在打开 BOSS 直聘...")
    page.get('https://www.zhipin.com')
    time.sleep(2)
    
    print()
    print("请确认浏览器中是否已登录 BOSS 直聘？")
    print("如果没有登录，请先手动登录，然后按回车键继续...")
    input()

    print()
    print("开始爬取...")
    print()

    session = None

    print("正在启动网络监听...")
    page.listen.start('joblist')
    
    for keyword in KEYWORDS:
        for city in CITIES:
            print()
            print("="*60)
            print(f"正在爬取: 关键词={keyword}, 城市={city}")
            print("="*60)
            
            city_code = get_city_code(city)

            if session is None:
                session = create_session_from_page(page, keyword, city_code)

            for page_num in range(1, PAGES_PER_KEYWORD + 1):
                try:
                    url = f'https://www.zhipin.com/web/geek/job?query={keyword}&city={city_code}&page={page_num}'
                    print(f"\n正在访问第 {page_num} 页: {url}")
                    
                    page.get(url)
                    random_delay(DELAY_MIN, DELAY_MAX)
                    
                    packet = None
                    try:
                        packet = page.listen.wait(timeout=10)
                    except:
                        print("等待数据包超时，尝试继续...")
                    
                    job_list = []
                    if packet and packet.response:
                        try:
                            data = packet.response.body
                            json_data = json.loads(data) if isinstance(data, str) else data
                            
                            if json_data.get('code') == 0:
                                zp_data = json_data.get('zpData', {})
                                job_list = zp_data.get('jobList', [])
                        except Exception as e:
                            print(f"解析响应失败: {e}")
                    
                    print(f"找到 {len(job_list)} 个岗位")
                    
                    for idx, job_item in enumerate(job_list):
                        try:
                            job_name = job_item.get('jobName', '')
                            company_name = job_item.get('brandName', '') or job_item.get('companyName', '')
                            salary_text = job_item.get('salaryDesc', '')
                            city_text = job_item.get('cityName', '')
                            experience = job_item.get('jobExperience', '')
                            education = job_item.get('jobDegree', '')
                            
                            company_size = (
                                job_item.get('brandScaleName', '') or
                                job_item.get('brandScale', '') or 
                                job_item.get('companySize', '') or 
                                job_item.get('scale', '')
                            )
                            company_industry = (
                                job_item.get('brandIndustry', '') or 
                                job_item.get('companyIndustry', '') or 
                                job_item.get('industry', '')
                            )

                            welfare = job_item.get('welfareList') or job_item.get('welfare') or job_item.get('welfareName')
                            if isinstance(welfare, list):
                                welfare = ','.join([str(x).strip() for x in welfare if str(x).strip()])
                            elif welfare is not None:
                                welfare = str(welfare).strip() or None
                            
                            security_id = job_item.get('securityId', '')
                            encrypt_job_id = job_item.get('encryptJobId', '')
                            referer = f'https://www.zhipin.com/job_detail/{encrypt_job_id}.html' if encrypt_job_id else None
                            detail_desc = fetch_job_detail_desc(session, page, security_id, referer) if session else None
                            job_desc = detail_desc or job_item.get('jobDesc', '') or job_item.get('jobDetail', '') or job_name
                            
                            salary_min, salary_max = parse_salary(salary_text)
                            clean_city = parse_city(city_text)
                            clean_education = parse_education(education)
                            skills = job_item.get('skills')
                            if isinstance(skills, list):
                                keywords = ','.join([str(x).strip() for x in skills if str(x).strip()])
                            elif isinstance(skills, str) and skills.strip():
                                keywords = skills.strip()
                            else:
                                keywords = extract_keywords(job_desc)
                            
                            job_data = {
                                'job_name': job_name,
                                'company_name': company_name,
                                'city': clean_city,
                                'salary_min': salary_min,
                                'salary_max': salary_max,
                                'experience': experience,
                                'education': clean_education,
                                'job_desc': job_desc,
                                'job_keywords': keywords,
                                'company_size': company_size,
                                'company_industry': company_industry,
                                'company_welfare': welfare
                            }
                            
                            if job_name and company_name:
                                if insert_job(job_data):
                                    print(f"✓ {job_name} @ {company_name}")
                                    total_inserted += 1
                        except Exception as e:
                            print(f"提取失败: {e}")
                            import traceback
                            traceback.print_exc()
                            continue
                
                except Exception as e:
                    print(f"⚠️ 发生错误: {e}")
                    import traceback
                    traceback.print_exc()
                    continue
                
                random_delay(DELAY_MIN, DELAY_MAX)
    
    print()
    print("="*60)
    print(f"爬取完成！共插入 {total_inserted} 条数据")
    print("="*60)
    print()
    print("按回车键退出...")
    input()
    
    page.quit()

if __name__ == '__main__':
    scrape_jobs()
