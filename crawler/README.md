# BOSS 直聘招聘数据爬虫

## 安装依赖

```bash
pip install -r requirements.txt
playwright install chromium
```

## 使用步骤

### 1. 配置数据库

编辑 `config.py`，修改数据库密码：

```python
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': '你的密码',  # 修改这里
    'database': 'job_data',
    'charset': 'utf8mb4'
}
```

### 2. 第一次运行（获取 Cookies）

```bash
python spider.py
```

浏览器会自动打开，跳转到 BOSS 直聘登录页：

1. 在浏览器中手动登录 BOSS 直聘
2. 登录成功后，在终端按回车键
3. Cookies 会自动保存到 `cookies.json`

### 3. 正常运行

获取 Cookies 后，再次运行：

```bash
python spider.py
```

程序会自动开始爬取数据。

## 爬取内容

- 关键词：Java、Python、前端、数据分析、产品经理
- 城市：北京、上海、广州、深圳、杭州
- 每个关键词爬取 3 页

## 反爬策略

- 随机延迟 5-10 秒
- 检测到验证码自动截图并暂停
- 使用真实浏览器登录的 Cookies

## 数据存储

数据自动存入 MySQL 数据库 `job_data.job_info` 表。
