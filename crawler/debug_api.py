
# 调试脚本：查看BOSS直聘API返回的完整数据结构
import json
import sys
import os

# 创建一个简单的调试脚本来查看API返回
debug_script = '''
# 调试脚本：查看API返回的数据结构
import json
import time
from DrissionPage import ChromiumPage, ChromiumOptions

print("="*60)
print("BOSS直聘API调试工具")
print("="*60)
print()

options = ChromiumOptions()
options.set_user_agent('Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36')

page = ChromiumPage(addr_or_opts=options)
page.set.window.max()

print("正在打开BOSS直聘...")
page.get('https://www.zhipin.com')
print("请先登录，然后按回车键继续...")
input()

# 访问一个搜索页面
print("访问Java-北京搜索页...")
page.get('https://www.zhipin.com/web/geek/job?query=Java&city=101010100&page=1')

print()
print("等待页面加载...")
time.sleep(5)

print()
print("检查所有网络请求...")
# 打印所有请求记录
print()
print("请查看浏览器网络面板，找到 joblist 接口的响应数据")
print("然后复制JSON数据，我们来分析字段结构")
print()
print("按回车键后，脚本将尝试监听网络请求...")
input()

print()
print("开始监听 joblist 接口...")
page.listen.start('joblist')

# 刷新页面触发请求
print("刷新页面...")
page.refresh()

try:
    packet = page.listen.wait(timeout=15)
    if packet and packet.response:
        print()
        print("="*80)
        print("捕获到API响应！")
        print("="*80)
        
        data = packet.response.body
        print()
        print("响应长度:", len(data) if isinstance(data, str) else "binary")
        
        # 尝试解析JSON
        try:
            json_data = json.loads(data) if isinstance(data, str) else data
            print()
            print("完整的JSON结构（格式化）:")
            print("-" * 80)
            print(json.dumps(json_data, indent=2, ensure_ascii=False))
            
            print()
            print("="*80)
            print("zpData.jobList 中的第一个岗位数据:")
            print("="*80)
            if json_data.get('code') == 0:
                zp_data = json_data.get('zpData', {})
                job_list = zp_data.get('jobList', [])
                if job_list:
                    first_job = job_list[0]
                    print(json.dumps(first_job, indent=2, ensure_ascii=False))
                    print()
                    print("所有可用字段:", list(first_job.keys()))
                    
        except Exception as e:
            print("JSON解析失败:", e)
            print("原始响应前500字符:", str(data)[:500])
            
except Exception as e:
    print("等待超时或出错:", e)

print()
print("按回车键退出...")
input()

page.quit()
'''

with open(r'f:\JobDataAnalysis\crawler\debug_api.py', 'w', encoding='utf-8') as f:
    f.write(debug_script)

print("调试脚本已保存到 f:\\JobDataAnalysis\\crawler\\debug_api.py")
print("您可以手动运行这个脚本来查看API返回的数据结构")
