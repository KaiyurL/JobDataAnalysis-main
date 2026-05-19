
# 测试爬虫：只爬取1页，验证公司规模字段是否正常
import re
import time
import random
import pymysql
import jieba
import json
import os
from datetime import datetime
from DrissionPage import ChromiumPage, ChromiumOptions

# ========== 配置区域 ==========
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'job_data',
    'charset': 'utf8mb4'
}

KEYWORDS = ["Java"]
CITIES = ["北京"]
PAGES_PER_KEYWORD = 1  # 只爬1页测试
DELAY_MIN = 1
DELAY_MAX = 3
# ===============================

def get_city_code(city_name):
    city_codes = {
        '北京': '101010100',
        '上海': '101020100',
        '广州': '101280100',
        '深圳': '101280600',
        '杭州': '101210100'
    }
    return city_codes.get(city_name, '101010100')

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
    edu_map = {'博士': '博士', '硕士': '硕士', '本科': '本科', '大专': '大专', '高中': '高中'}
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
             experience, education, job_keywords, company_size, company_industry, publish_date)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            '''
            salary_avg = None
            if job_data['salary_min'] and job_data['salary_max']:
                salary_avg = round((job_data['salary_min'] + job_data['salary_max']) / 2, 2)
            
            cursor.execute(sql, (
                job_data['job_name'],
                job_data['company_name'],
                job_data['city'],
                job_data['salary_min'],
                job_data['salary_max'],
                salary_avg,
                job_data['experience'],
                job_data['education'],
                job_data['job_keywords'],
                job_data['company_size'],
                job_data['company_industry'],
                datetime.now().date()
            ))
        conn.commit()
        return True
    except Exception as e:
        print(f'插入失败: {e}')
        conn.rollback()
        return False
    finally:
        conn.close()

def random_delay():
    delay = random.uniform(DELAY_MIN, DELAY_MAX)
    print(f'等待 {delay:.1f} 秒...')
    time.sleep(delay)

def scrape_jobs():
    total_inserted = 0
    
    print()
    print('='*60)
    print('BOSS 直聘招聘数据爬虫 - 测试版本')
    print('='*60)
    print()
    
    print('正在启动浏览器...')
    options = ChromiumOptions()
    page = ChromiumPage(addr_or_opts=options)
    
    print()
    print('浏览器已启动，正在打开 BOSS 直聘...')
    page.get('https://www.zhipin.com')
    time.sleep(2)
    
    print()
    print('请确认浏览器中是否已登录 BOSS 直聘？')
    print('如果没有登录，请先手动登录，然后按回车键继续...')
    input()
    
    print()
    print(f'关键词: {KEYWORDS}')
    print(f'城市: {CITIES}')
    print(f'每关键词页数: {PAGES_PER_KEYWORD}')
    print()
    print('开始爬取...')
    print()
    
    print('正在启动网络监听...')
    page.listen.start('joblist')
    
    for keyword in KEYWORDS:
        for city in CITIES:
            print('\n' + '='*60)
            print(f'正在爬取: 关键词={keyword}, 城市={city}')
            print('='*60)
            
            city_code = get_city_code(city)
            
            for page_num in range(1, PAGES_PER_KEYWORD + 1):
                try:
                    url = f'https://www.zhipin.com/web/geek/job?query={keyword}&city={city_code}&page={page_num}'
                    print(f'\n正在访问第 {page_num} 页: {url}')
                    
                    page.get(url)
                    random_delay()
                    
                    packet = None
                    try:
                        packet = page.listen.wait(timeout=10)
                    except:
                        print('等待数据包超时，尝试继续...')
                    
                    job_list = []
                    if packet and packet.response:
                        try:
                            data = packet.response.body
                            json_data = json.loads(data) if isinstance(data, str) else data
                            
                            if json_data.get('code') == 0:
                                zp_data = json_data.get('zpData', {})
                                job_list = zp_data.get('jobList', [])
                        except Exception as e:
                            print(f'解析响应失败: {e}')
                    
                    print(f'找到 {len(job_list)} 个岗位')
                    
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
                            
                            if idx < 3:
                                print(f"\n[DEBUG #{idx+1}] 公司名: {company_name}")
                                print(f"[DEBUG #{idx+1}] 公司规模: '{company_size}'")
                                print(f"[DEBUG #{idx+1}] 公司行业: '{company_industry}'")
                            
                            job_desc = job_item.get('jobDesc', '') or job_item.get('jobDetail', '') or job_name
                            
                            salary_min, salary_max = parse_salary(salary_text)
                            clean_city = parse_city(city_text)
                            clean_education = parse_education(education)
                            keywords = extract_keywords(job_desc)
                            
                            job_data = {
                                'job_name': job_name,
                                'company_name': company_name,
                                'city': clean_city,
                                'salary_min': salary_min,
                                'salary_max': salary_max,
                                'experience': experience,
                                'education': clean_education,
                                'job_keywords': keywords,
                                'company_size': company_size,
                                'company_industry': company_industry
                            }
                            
                            if job_name and company_name:
                                if insert_job(job_data):
                                    print(f'✓ {job_name} @ {company_name}')
                                    total_inserted += 1
                        except Exception as e:
                            print(f'提取失败: {e}')
                            import traceback
                            traceback.print_exc()
                            continue
                    
                except Exception as e:
                    print(f'⚠️ 发生错误: {e}')
                    import traceback
                    traceback.print_exc()
                    continue
                
                random_delay()
    
    print('\n' + '='*60)
    print(f'测试完成！共插入 {total_inserted} 条数据')
    print('='*60)
    print()
    print('按回车键退出...')
    input()
    
    page.quit()

if __name__ == '__main__':
    scrape_jobs()
