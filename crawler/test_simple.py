import time
from playwright.sync_api import sync_playwright

def test():
    print('正在打开浏览器（最干净模式）...')
    
    with sync_playwright() as p:
        # 直接用 Playwright 自带的 Chromium，不加任何特殊参数
        browser = p.chromium.launch(
            headless=False
        )
        
        page = browser.new_page()
        
        print('正在访问 BOSS 直聘...')
        try:
            page.goto('https://www.zhipin.com/', timeout=60000)
            print('页面已打开！请观察是否还是空白...')
            print('按回车键退出...')
            input()
        except Exception as e:
            print(f'访问出错：{e}')
            print('请观察浏览器，按回车退出...')
            input()
        
        browser.close()

if __name__ == '__main__':
    try:
        test()
    except Exception as e:
        print(f'错误：{e}')
        import traceback
        traceback.print_exc()
        input('按回车键退出...')
