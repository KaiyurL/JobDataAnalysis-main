
import pymysql
import csv
from collections import defaultdict

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456ppoo',
    'database': 'job_data',
    'charset': 'utf8mb4'
}

OUTPUT_FILE = 'keyword_freq_full.csv'
TOP_N = 300

def main():
    print("="*60)
    print("关键词频率分析工具")
    print("="*60)
    print()
    
    skill_counts = defaultdict(int)
    total_records = 0
    records_with_keywords = 0
    
    try:
        conn = pymysql.connect(**DB_CONFIG)
        print("✓ 数据库连接成功")
        
        with conn.cursor() as cursor:
            sql = "SELECT id, job_keywords FROM job_info WHERE job_keywords IS NOT NULL AND job_keywords != ''"
            cursor.execute(sql)
            results = cursor.fetchall()
            
            print(f"✓ 共找到 {len(results)} 条包含关键词的数据")
            print()
            
            for job_id, keywords in results:
                total_records += 1
                
                if not keywords:
                    continue
                
                keyword_list = [k.strip().lower() for k in keywords.split(',') if k.strip()]
                
                if keyword_list:
                    records_with_keywords += 1
                    for kw in keyword_list:
                        skill_counts[kw] += 1
        
        print(f"✓ 处理完成，{records_with_keywords} 条记录包含有效关键词")
        print(f"✓ 共发现 {len(skill_counts)} 个不同的关键词")
        print()
        
        sorted_skills = sorted(skill_counts.items(), key=lambda x: (-x[1], x[0]))
        
        print(f"Top {TOP_N} 关键词：")
        print("-"*60)
        print(f"{'排名':<6} {'关键词':<30} {'出现次数':<10}")
        print("-"*60)
        
        for idx, (skill, count) in enumerate(sorted_skills[:TOP_N], 1):
            print(f"{idx:<6} {skill:<30} {count:<10}")
        
        print()
        print(f"正在写入 {OUTPUT_FILE}...")
        with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.writer(f)
            writer.writerow(['排名', '关键词', '出现次数'])
            
            for idx, (skill, count) in enumerate(sorted_skills[:TOP_N], 1):
                writer.writerow([idx, skill, count])
        
        print(f"✓ 文件 {OUTPUT_FILE} 已成功保存！")
        print()
        print("="*60)
        print("分析完成！")
        print("="*60)
        
    except Exception as e:
        print(f"✗ 错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if 'conn' in locals():
            conn.close()

if __name__ == '__main__':
    main()
