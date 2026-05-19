import re
import time
import random
import pymysql
import jieba
import os
from datetime import datetime
import requests

# ========== 配置区域 ==========
# 请在 Chrome 登录 BOSS 直聘后，复制请求的 Cookie 粘贴到下面
COOKIE_STR = ''

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456ppoo',
    'database': 'job_data',
    'charset': 'utf8mb4'
}

KEYWORDS = ["Java"]  # 先只爬1个关键词测试
CITIES = ["北京"]    # 先只爬1个城市测试
PAGES_PER_KEYWORD = 1  # 先只爬1页测试
DELAY_MIN = 3  # 最小延迟秒数
DELAY_MAX = 6  # 最大延迟秒数
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

def get_headers():
    return {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        'Cookie': COOKIE_STR,
        'Referer': 'https://www.zhipin.com/',
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'zh-CN,zh;q=0.9',
        'X-Requested-With': 'XMLHttpRequest'
    }

def scrape_jobs():
    if not COOKIE_STR:
        print()
        print('='*60)
        print('⚠️ 请先在代码中填写 COOKIE_STR！')
        print('如何获取：')
        print('1. 用 Chrome 浏览器登录 BOSS 直聘')
        print('2. 按 F12 打开开发者工具，切换到 Network 标签')
        print('3. 刷新页面，找到任意请求')
        print('4. 在 Request Headers 中找到 Cookie，复制全部内容')
        print('5. 粘贴到代码中 COOKIE_STR = \'\' 里面')
        print('='*60)
        print()
        return
    
    total_inserted = 0
    
    print()
    print('='*60)
    print('BOSS 直聘招聘数据爬虫 (Requests版)')
    print('='*60)
    print()
    print(f'关键词: {KEYWORDS}')
    print(f'城市: {CITIES}')
    print(f'每关键词页数: {PAGES_PER_KEYWORD}')
    print()
    print('开始爬取...')
    print()
    
    for keyword in KEYWORDS:
        for city in CITIES:
            print('\n' + '='*60)
            print(f'正在爬取: 关键词={keyword}, 城市={city}')
            print('='*60)
            
            city_code = get_city_code(city)
            
            for page_num in range(1, PAGES_PER_KEYWORD + 1):
                url = f'https://www.zhipin.com/web/geek/joblist?query={keyword}&city={city_code}&page={page_num}'
                
                try:
                    print(f'\n正在访问第 {page_num} 页: {url}')
                    
                    response = requests.get(url, headers=get_headers(), timeout=30)
                    
                    if response.status_code != 200:
                        print(f'⚠️ 请求失败，状态码: {response.status_code}')
                        print(f'响应内容: {response.text[:500]}')
                        continue
                    
                    data = response.json()
                    
                    # BOSS直聘返回的数据结构，根据实际JSON结构调整
                    if data.get('code') != 0:
                        print(f'⚠️ 接口返回错误: {data.get("message")}')
                        print(f'完整响应: {data}')
                        continue
                    
                    job_list = []
                    # 尝试解析数据，根据实际JSON结构调整
                    if data.get('zpData') and data['zpData'].get('jobList'):
                        job_list = data['zpData']['jobList']
                    
                    print(f'找到 {len(job_list)} 个岗位')
                    
                    for job_item in job_list:
                        try:
                            job_name = job_item.get('jobName', '')
                            company_name = job_item.get('brandName', '') or job_item.get('companyName', '')
                            salary_text = job_item.get('salaryDesc', '')
                            city_text = job_item.get('cityName', '')
                            experience = job_item.get('jobExperience', '')
                            education = job_item.get('jobDegree', '')
                            company_size = job_item.get('brandScale', '') or job_item.get('companySize', '')
                            company_industry = job_item.get('brandIndustry', '') or job_item.get('companyIndustry', '')
                            
                            # 尝试获取描述用于提取关键词
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
                            continue
                        
                except Exception as e:
                    print(f'⚠️ 发生错误: {e}')
                    continue
                
                random_delay()
    
    print('\n' + '='*60)
    print(f'爬虫完成！共插入 {total_inserted} 条数据')
    print('='*60)
    print()
    print('按回车键退出...')
    input()

if __name__ == '__main__':
    try:
        scrape_jobs()
    except Exception as e:
        print(f'\n错误：{e}')
        import traceback
        traceback.print_exc()
        print()
        print('按回车键退出...')
        input()
