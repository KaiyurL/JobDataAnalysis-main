
# 快速检查数据库中的现有数据
import pymysql

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456ppoo',
    'database': 'job_data',
    'charset': 'utf8mb4'
}

print("="*60)
print("检查数据库中的数据")
print("="*60)

conn = pymysql.connect(**DB_CONFIG)
try:
    with conn.cursor() as cursor:
        cursor.execute("SELECT COUNT(*) FROM job_info")
        count = cursor.fetchone()[0]
        print(f"\n总数据量: {count}")
        
        print("\n=== 查看前10条数据（公司信息字段） ===")
        cursor.execute("""
            SELECT id, job_name, company_name, company_size, company_industry 
            FROM job_info 
            ORDER BY id DESC 
            LIMIT 10
        """)
        rows = cursor.fetchall()
        
        print(f"{'ID':<5} {'公司名称':<20} {'公司规模':<15} {'公司行业':<15}")
        print("-"*60)
        for row in rows:
            id, job_name, company_name, company_size, company_industry = row
            print(f"{id:<5} {company_name:<20} {str(company_size):<15} {str(company_industry):<15}")
            
finally:
    conn.close()

print("\n✅ 检查完成！")
