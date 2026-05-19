
import pymysql

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456ppoo',
    'database': 'job_data',
    'charset': 'utf8mb4'
}

def main():
    print("="*60)
    print("查看 job_info 表结构")
    print("="*60)
    print()
    
    try:
        conn = pymysql.connect(**DB_CONFIG)
        print("✓ 数据库连接成功")
        print()
        
        with conn.cursor() as cursor:
            # 查看表结构
            print("--- 表结构 ---")
            cursor.execute("DESCRIBE job_info")
            columns = cursor.fetchall()
            
            for col in columns:
                col_name, col_type, null_ok, key, default, extra = col
                print(f"{col_name:<25} {col_type}")
            
            print()
            print("--- 前5条数据样本 ---")
            cursor.execute("SELECT * FROM job_info LIMIT 5")
            rows = cursor.fetchall()
            
            for row in rows:
                print(row)
                print()
            
    except Exception as e:
        print(f"✗ 错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if 'conn' in locals():
            conn.close()

if __name__ == '__main__':
    main()
