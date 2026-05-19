import json
import os
from playwright.sync_api import sync_playwright

COOKIES_FILE = 'boss_cookies.json'

def save_cookies():
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=False,
            channel='chrome',
            args=[
                '--disable-blink-features=AutomationControlled',
                '--no-sandbox',
                '--disable-infobars',
                '--disable-dev-shm-usage',
                '--start-maximized'
            ]
        )
        
        context = browser.new_context(
            viewport={'width': 1920, 'height': 1080},
            user_agent='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            locale='zh-CN',
            timezone_id='Asia/Shanghai'
        )
        
        page = context.new_page()
        
        print('='*60)
        print('BOSS 直聘 Cookie 获取工具')
        print('='*60)
        print()
        print('正在打开 BOSS 直聘首页...')
        
        try:
            page.goto('https://www.zhipin.com/')
            print('✓ 页面已打开')
        except Exception as e:
            print(f'⚠️ 请在浏览器中手动访问 https://www.zhipin.com/')
        
        print()
        print('请按以下步骤操作：')
        print('  1. 在浏览器中登录 BOSS 直聘')
        print('  2. 登录成功后，在此终端按回车键保存 Cookies')
        print()
        
        input('登录完成后，按回车键继续...')
        
        context.storage_state(path=COOKIES_FILE)
        
        print()
        print(f'✓ Cookies 已保存到 {COOKIES_FILE}')
        print()
        
        browser.close()

if __name__ == '__main__':
    save_cookies()
