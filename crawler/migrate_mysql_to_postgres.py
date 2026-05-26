import json
import os
from typing import Any, Dict, List, Tuple

import pymysql
import psycopg2
import psycopg2.extras


RUNTIME_CONFIG_FILE = os.environ.get("JOBDATA_RUNTIME_CONFIG") or os.path.join(os.path.dirname(__file__), "runtime_config.json")


def load_runtime_config() -> Dict[str, Any]:
    if not os.path.exists(RUNTIME_CONFIG_FILE):
        raise RuntimeError(f"找不到配置文件: {RUNTIME_CONFIG_FILE}")
    with open(RUNTIME_CONFIG_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict) or not isinstance(data.get("db"), dict):
        raise RuntimeError("配置文件格式错误: 需要包含 db 配置")
    return data


def get_mysql_cfg() -> Dict[str, Any]:
    return {
        "host": os.environ.get("MYSQL_HOST", "localhost"),
        "port": int(os.environ.get("MYSQL_PORT", "3306")),
        "user": os.environ.get("MYSQL_USER", "root"),
        "password": os.environ.get("MYSQL_PASSWORD", "123456ppoo"),
        "database": os.environ.get("MYSQL_DB", "job_data"),
        "charset": "utf8mb4",
    }


def get_pg_cfg() -> Dict[str, Any]:
    rc = load_runtime_config()
    db = rc.get("db") or {}
    return {
        "host": db.get("host") or "localhost",
        "port": int(db.get("port") or 5432),
        "user": db.get("user") or "postgres",
        "password": db.get("password") or "12345ppoo",
        "dbname": db.get("database") or "job_data",
    }


def mysql_select_all(cur, table: str) -> Tuple[List[str], List[Tuple[Any, ...]]]:
    cur.execute(f"SELECT * FROM `{table}`")
    cols = [d[0] for d in cur.description]
    rows = cur.fetchall()
    return cols, rows


def pg_table_exists(cur, table: str) -> bool:
    cur.execute(
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name=%s)",
        (table,),
    )
    return bool(cur.fetchone()[0])


def pg_bulk_insert(cur, table: str, cols: List[str], rows: List[Tuple[Any, ...]]):
    if not rows:
        return
    col_sql = ",".join([f"\"{c}\"" for c in cols])
    sql = f"INSERT INTO {table} ({col_sql}) VALUES %s"
    psycopg2.extras.execute_values(cur, sql, rows, page_size=1000)


def pg_fix_sequence(cur, table: str, id_col: str = "id"):
    cur.execute(
        """
        SELECT pg_get_serial_sequence(%s, %s)
        """,
        (table, id_col),
    )
    seq = cur.fetchone()[0]
    if not seq:
        return
    
    cur.execute(f"SELECT COALESCE(MAX({id_col}), 0) FROM {table}")
    max_id = int(cur.fetchone()[0] or 0)
    
    if max_id == 0:
        # 表为空，序列从 1 开始
        cur.execute("SELECT setval(%s, 1, false)", (seq,))
    else:
        # 表非空，设置序列为当前最大值（下次插入会从 max_id+1 开始）
        cur.execute("SELECT setval(%s, %s, true)", (seq, max_id))


def main():
    mysql_cfg = get_mysql_cfg()
    pg_cfg = get_pg_cfg()

    table_map = [
        ("job_info", "job_info"),
        ("job_info_51job", "job_info_51job"),
        ("user", "users"),
        ("user_profile", "user_profile"),
        ("user_favorite_job", "user_favorite_job"),
        ("user_job_history", "user_job_history"),
        ("user_match_history", "user_match_history"),
    ]

    mysql_conn = pymysql.connect(**mysql_cfg)
    pg_conn = psycopg2.connect(**pg_cfg)
    try:
        with mysql_conn.cursor() as my_cur, pg_conn.cursor() as pg_cur:
            for mysql_table, pg_table in table_map:
                cols, rows = mysql_select_all(my_cur, mysql_table)
                if not pg_table_exists(pg_cur, pg_table):
                    raise RuntimeError(f"PostgreSQL 缺少目标表 {pg_table}，请先启动后端执行 Flyway 初始化。")

                pg_cur.execute(f"DELETE FROM {pg_table}")
                pg_bulk_insert(pg_cur, pg_table, cols, rows)
                if "id" in cols:
                    pg_fix_sequence(pg_cur, pg_table, "id")

                pg_conn.commit()
                print(f"迁移完成: {mysql_table} -> {pg_table} rows={len(rows)}")
    finally:
        try:
            mysql_conn.close()
        except Exception:
            pass
        try:
            pg_conn.close()
        except Exception:
            pass


if __name__ == "__main__":
    main()
