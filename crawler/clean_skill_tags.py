
import pymysql
import sys

DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456ppoo',
    'database': 'job_data',
    'charset': 'utf8mb4'
}

WHITELIST_FILE = 'skill_whitelist.txt'

def load_whitelist():
    whitelist = set()
    try:
        with open(WHITELIST_FILE, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line:
                    whitelist.add(line.lower())
        return whitelist
    except FileNotFoundError:
        print(f"✗ 错误: 找不到白名单文件 {WHITELIST_FILE}")
        print("请先运行 generate_whitelist.py！")
        return None

def main():
    print("="*60)
    print("技能标签清洗工具")
    print("="*60)
    print()
    
    whitelist = load_whitelist()
    if not whitelist:
        return
    
    print(f"✓ 加载白名单，共 {len(whitelist)} 个技能词")
    print()
    
    try:
        conn = pymysql.connect(**DB_CONFIG)
        print("✓ 数据库连接成功")
        print()
        
        before_stats = {
            'total_records': 0,
            'records_with_keywords': 0,
            'total_keywords': 0,
            'avg_keywords': 0
        }
        
        after_stats = {
            'total_records': 0,
            'records_with_keywords': 0,
            'records_cleaned': 0,
            'records_cleared': 0,
            'total_keywords': 0,
            'avg_keywords': 0,
            'removed_keywords': 0
        }
        
        all_job_data = []
        
        with conn.cursor() as cursor:
            cursor.execute("SELECT id, job_keywords FROM job_info")
            results = cursor.fetchall()
            before_stats['total_records'] = len(results)
            
            print("--- 清洗前统计 ---")
            for job_id, keywords in results:
                if keywords:
                    before_stats['records_with_keywords'] += 1
                    kw_list = [k.strip().lower() for k in keywords.split(',') if k.strip()]
                    before_stats['total_keywords'] += len(kw_list)
                    all_job_data.append((job_id, keywords, kw_list))
                else:
                    all_job_data.append((job_id, None, []))
            
            before_stats['avg_keywords'] = (
                before_stats['total_keywords'] / before_stats['records_with_keywords'] 
                if before_stats['records_with_keywords'] else 0
            )
            
            print(f"总记录数: {before_stats['total_records']}")
            print(f"有关键词的记录: {before_stats['records_with_keywords']}")
            print(f"总关键词数: {before_stats['total_keywords']}")
            print(f"平均每岗位技能数: {before_stats['avg_keywords']:.2f}")
            print()
        
        print("正在清洗数据...")
        
        updates = []
        
        for job_id, original, kw_list in all_job_data:
            cleaned_kws = []
            removed_count = 0
            
            for kw in kw_list:
                kw_lower = kw.lower()
                if kw_lower in whitelist:
                    cleaned_kws.append(kw_lower)
                else:
                    removed_count += 1
            
            after_stats['removed_keywords'] += removed_count
            
            # 去重
            cleaned_kws = list(set(cleaned_kws))
            
            if cleaned_kws:
                new_value = ','.join(cleaned_kws)
                after_stats['records_with_keywords'] += 1
                after_stats['total_keywords'] += len(cleaned_kws)
            else:
                new_value = None
                after_stats['records_cleared'] += 1
            
            if new_value != original:
                updates.append((new_value, job_id))
        
        after_stats['total_records'] = before_stats['total_records']
        after_stats['records_cleaned'] = len(updates)
        after_stats['avg_keywords'] = (
            after_stats['total_keywords'] / after_stats['records_with_keywords'] 
            if after_stats['records_with_keywords'] else 0
        )
        
        print(f"✓ 预处理完成，需要更新 {len(updates)} 条记录")
        print()
        
        # 执行更新
        if updates:
            confirm = input("确认要更新数据库吗？(yes/no): ")
            if confirm.lower() != 'yes':
                print("操作已取消")
                return
            
            with conn.cursor() as cursor:
                sql = "UPDATE job_info SET job_keywords = %s WHERE id = %s"
                cursor.executemany(sql, updates)
            conn.commit()
            print(f"✓ 已成功更新 {len(updates)} 条记录！")
            print()
        
        # 显示对比
        print("="*60)
        print("清洗前后对比")
        print("="*60)
        print()
        print(f"{'指标':<25} {'清洗前':<15} {'清洗后':<15} {'变化':<10}")
        print("-"*65)
        
        rows = [
            ("总记录数", before_stats['total_records'], after_stats['total_records']),
            ("有关键词的记录", before_stats['records_with_keywords'], after_stats['records_with_keywords']),
            ("被清空的记录", 0, after_stats['records_cleared']),
            ("被修改的记录", 0, after_stats['records_cleaned']),
            ("总关键词数", before_stats['total_keywords'], after_stats['total_keywords']),
            ("移除的关键词数", 0, after_stats['removed_keywords']),
            ("平均每岗位技能数", f"{before_stats['avg_keywords']:.2f}", f"{after_stats['avg_keywords']:.2f}")
        ]
        
        for label, before, after in rows:
            try:
                diff = int(after) - int(before)
                if diff == 0:
                    diff_str = "-"
                else:
                    diff_str = f"{diff:+.0f}"
                print(f"{label:<25} {before:<15} {after:<15} {diff_str:<10}")
            except:
                print(f"{label:<25} {before:<15} {after:<15} -")
        
        print()
        print("="*60)
        print("清洗完成！")
        print("="*60)
        print()
        print("建议: 重新运行后端服务使缓存失效，然后检查前端显示效果！")
        
    except Exception as e:
        print(f"✗ 错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if 'conn' in locals():
            conn.close()

if __name__ == '__main__':
    main()
