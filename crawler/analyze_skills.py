
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

OUTPUT_FILE = 'skill_freq.csv'
TOP_N = 200

def main():
    print("="*60)
    print("技能标签频率分析工具")
    print("="*60)
    print()
    
    # 统计字典
    skill_counts = defaultdict(int)
    total_records = 0
    records_with_skills = 0
    
    try:
        conn = pymysql.connect(**DB_CONFIG)
        print("✓ 数据库连接成功")
        
        with conn.cursor() as cursor:
            # 查询所有非空的 job_keywords
            sql = "SELECT id, job_keywords FROM job_info WHERE job_keywords IS NOT NULL AND job_keywords != ''"
            cursor.execute(sql)
            results = cursor.fetchall()
            
            print(f"✓ 共找到 {len(results)} 条包含技能标签的数据")
            print()
            
            for job_id, skill_tags in results:
                total_records += 1
                
                if not skill_tags:
                    continue
                
                # 拆分、清洗、统计
                skills = [s.strip().lower() for s in skill_tags.split(',') if s.strip()]
                
                if skills:
                    records_with_skills += 1
                    for skill in skills:
                        skill_counts[skill] += 1
        
        print(f"✓ 处理完成，{records_with_skills} 条记录包含有效技能")
        print(f"✓ 共发现 {len(skill_counts)} 个不同的技能标签")
        print()
        
        # 按频率降序排序
        sorted_skills = sorted(skill_counts.items(), key=lambda x: (-x[1], x[0]))
        
        # 输出到控制台
        print(f"Top {TOP_N} 技能标签：")
        print("-"*60)
        print(f"{'排名':<6} {'技能名称':<30} {'出现次数':<10}")
        print("-"*60)
        
        for idx, (skill, count) in enumerate(sorted_skills[:TOP_N], 1):
            print(f"{idx:<6} {skill:<30} {count:<10}")
        
        # 输出到 CSV
        print()
        print(f"正在写入 {OUTPUT_FILE}...")
        with open(OUTPUT_FILE, 'w', newline='', encoding='utf-8-sig') as f:
            writer = csv.writer(f)
            writer.writerow(['排名', '技能名称', '出现次数'])
            
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
