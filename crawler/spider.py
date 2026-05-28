"""
爬虫调度入口

职责：
- 读取 crawler/config.json 配置
- 初始化数据库表结构（PostgreSQL）
- 启动/连接 DrissionPage 浏览器
- 按 --platform (boss/51job/both) 分发到对应爬虫模块执行

依赖模块：
- spider_call/boss_spider.py: run_boss
- spider_call/job51_spider.py: run_51job
- spider_call/spider_common.py: 配置/DB/浏览器查找等公共能力
"""

import os
import sys
import time
import argparse

from DrissionPage import ChromiumPage, ChromiumOptions
from DrissionPage.errors import BrowserConnectError

try:
    from spider_call.spider_common import load_config, ensure_db_tables, find_browser_path
    from spider_call.boss_spider import run_boss
    from spider_call.job51_spider import run_51job
except ModuleNotFoundError:
    from .spider_call.spider_common import load_config, ensure_db_tables, find_browser_path
    from .spider_call.boss_spider import run_boss
    from .spider_call.job51_spider import run_51job

def scrape_jobs(platform_override=None, browser_override=None):
    """
    执行爬虫主流程。

    参数：
    - platform_override: 覆盖配置中的 platform（boss/51job/both）
    - browser_override: 覆盖配置中的 browser（auto/edge/chrome）
    """
    config = load_config()
    platform = str(platform_override or config.get("platform", "boss")).strip().lower()
    browser = str(browser_override or config.get("browser", "auto")).strip().lower()
    if sys.stdin.isatty() and not platform_override:
        selected = input(f"请选择数据源(boss/51job/both) [默认 {platform}]: ").strip().lower()
        if selected in ("boss", "51job", "both"):
            platform = selected

    if sys.stdin.isatty() and not browser_override:
        selected_browser = input(f"请选择浏览器(auto/edge/chrome) [默认 {browser}]: ").strip().lower()
        if selected_browser in ("auto", "edge", "chrome"):
            browser = selected_browser

    KEYWORDS = config.get("keywords", ["Java", "Python", "前端", "数据分析", "产品经理"])
    CITIES = config.get("cities", ["北京", "上海", "广州", "深圳", "杭州"])
    PAGES_PER_KEYWORD = int(config.get("pages_per_keyword", 2))
    PAGES_PER_CITY_51JOB = int(config.get("pages_per_city_51job", 2))
    DELAY_MIN = int(config.get("delay_min", 3))
    DELAY_MAX = int(config.get("delay_max", 8))

    total_inserted = 0

    print()
    print("=" * 60)
    print("招聘数据爬虫 (DrissionPage版)")
    print("=" * 60)
    print()

    try:
        ensure_db_tables()
        print("已确保数据表存在: job_info / job_info_51job")
    except Exception as e:
        print(f"初始化数据表失败: {e}")
        return

    print("配置:")
    print(f"  数据源: {platform}")
    print(f"  关键词: {', '.join(KEYWORDS)}")
    print(f"  城市: {', '.join(CITIES)}")
    print(f"  Boss每关键词页数: {PAGES_PER_KEYWORD}")
    print(f"  51job每城市页数: {PAGES_PER_CITY_51JOB}")
    print(f"  延迟范围: {DELAY_MIN}-{DELAY_MAX} 秒")
    print(f"  浏览器: {browser}")
    print()
    print("正在启动浏览器...")

    browser_path = find_browser_path(browser)
    if not browser_path:
        print("未找到可用的浏览器可执行文件(Edge/Chrome/Chromium)。")
        return

    tmp_path = os.path.join(os.path.dirname(__file__), "tmp")
    os.makedirs(tmp_path, exist_ok=True)

    options = ChromiumOptions(read_file=False)
    options.set_browser_path(browser_path)
    options.auto_port(tmp_path=tmp_path)
    try:
        page = ChromiumPage(addr_or_opts=options)
    except BrowserConnectError as e:
        print(str(e).strip())
        print()
        print("尝试连接已启动的调试浏览器：127.0.0.1:9222")
        options2 = ChromiumOptions(read_file=False)
        options2.set_browser_path(browser_path)
        options2.set_local_port(9222)
        options2.existing_only(True)
        try:
            page = ChromiumPage(addr_or_opts=options2)
        except BrowserConnectError:
            print()
            print("当前环境无法启动/连接 Chrome 调试端口，爬虫需要可用的远程调试浏览器。")
            print("你可以在本机手动启动浏览器（新用户目录，避免和已打开的浏览器冲突）：")
            print(f"\"{browser_path}\" --remote-debugging-port=9222 --user-data-dir=$env:TEMP\\dp_boss --disable-gpu about:blank")
            print()
            print("启动后再运行 python spider.py。")
            return

    try:
        if platform in ("boss", "both"):
            print()
            print("浏览器已启动，正在打开 BOSS 直聘...")
            page.get("https://www.zhipin.com")
            time.sleep(2)

            print()
            print("请确认浏览器中是否已登录 BOSS 直聘？")
            print("如果没有登录，请先手动登录，然后按回车键继续...")
            input()

            print()
            print("开始爬取 BOSS 直聘...")
            print()
            total_inserted += run_boss(page, KEYWORDS, CITIES, PAGES_PER_KEYWORD, DELAY_MIN, DELAY_MAX)

        if platform in ("51job", "qcwy", "51", "both"):
            total_inserted += run_51job(page, config, KEYWORDS, CITIES, PAGES_PER_CITY_51JOB, DELAY_MIN, DELAY_MAX)

        print()
        print("=" * 60)
        print(f"爬取完成！共插入 {total_inserted} 条数据")
        print("=" * 60)
        print()
        if sys.stdin.isatty():
            print("按回车键退出...")
            input()
    finally:
        try:
            page.quit()
        except Exception:
            pass

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--platform", choices=["boss", "51job", "both"])
    parser.add_argument("--browser", choices=["auto", "edge", "chrome"])
    args = parser.parse_args()
    scrape_jobs(platform_override=args.platform, browser_override=args.browser)
