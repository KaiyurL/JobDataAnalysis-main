import os

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '123456ppoo',  # 请修改为你的密码
    'database': 'job_data',
    'charset': 'utf8mb4'
}

# 爬取配置
KEYWORDS = ["Java", "Python", "前端", "数据分析", "产品经理"]
CITIES = ["北京", "上海", "广州", "深圳", "杭州"]
PAGES_PER_KEYWORD = 3  # 每个关键词爬取的页数
DELAY_MIN = 5  # 最小延迟（秒）
DELAY_MAX = 10  # 最大延迟（秒）

# 文件路径
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
COOKIES_FILE = os.path.join(BASE_DIR, 'cookies.json')
SCREENSHOT_DIR = os.path.join(BASE_DIR, 'screenshots')

# 创建截图目录
os.makedirs(SCREENSHOT_DIR, exist_ok=True)
