
import csv
from collections import defaultdict

INPUT_FILE = 'keyword_freq_full.csv'
WHITELIST_FILE = 'skill_whitelist.txt'
REMOVED_FILE = 'removed_terms.txt'

CITIES = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '西安', '重庆', '南京',
          '苏州', '天津', '郑州', '长沙', '青岛', '大连', '厦门', '宁波', '无锡', '合肥',
          '福州', '济南', '昆明', '南昌', '哈尔滨', '沈阳', '长春', '石家庄', '太原']

STOPWORDS = ['以上', '优先', '经验', '负责', '工作', '我们', '要求', '岗位', '任职', '职责',
             '具有', '能够', '进行', '需要', '良好', '具备', '相关', '熟悉', '了解', '熟练',
             '掌握', '使用', '参与', '负责', '管理', '开发', '维护', '支持', '协助', '配合',
             '完成', '执行', '实施', '推动', '提升', '优化', '改进', '建立', '完善', '制定',
             '分析', '研究', '设计', '实现', '测试', '部署', '运维', '监控', '优化', '升级',
             '团队', '项目', '产品', '业务', '系统', '技术', '能力', '素养', '专业', '学历',
             '本科', '硕士', '大专', '博士', '应届', '毕业', '在校', '实习', '兼职', '全职',
             '福利', '待遇', '薪资', '薪酬', '福利', '奖金', '补贴', '保险', '社保', '养老',
             '医疗', '失业', '工伤', '生育', '公积金', '年假', '调休', '加班', '双休', '单休',
             '大小周', '弹性', '带薪', '假期', '节日', '生日', '团建', '旅游', '体检', '零食',
             '咖啡', '下午茶', '健身房', '俱乐部', '活动', '会议', '培训', '学习', '发展',
             '晋升', '成长', '空间', '机会', '平台', '环境', '氛围', '同事', '领导', '老板',
             '公司', '企业', '集团', '股份', '有限', '责任', '科技', '网络', '信息', '软件',
             '技术', '服务', '咨询', '数据', '智能', '互联', '电商', '金融', '教育', '医疗',
             '文化', '传媒', '广告', '游戏', '娱乐', '体育', '物流', '贸易', '制造', '生产']

COMPANY_TERMS = ['京东', '美团', '阿里', '腾讯', '百度', '字节', '跳动', '快手', '滴滴',
                 '网易', '小米', '华为', 'oppo', 'vivo', '三星', '苹果', '微软', '谷歌',
                 '亚马逊', 'facebook', 'twitter', 'linkedin', 'instagram', 'tiktok',
                 '阿里巴巴', '蚂蚁', '支付宝', '微信', 'qq', '新浪微博', '知乎', '豆瓣',
                 '拼多多', '苏宁', '国美', '京东', '天猫', '淘宝', '1688', '阿里巴巴',
                 '哔哩哔哩', 'bilibili', 'b站', '爱奇艺', '腾讯视频', '优酷', '芒果tv',
                 '字节跳动', '今日头条', '抖音', '火山', '西瓜', '快手', '微视', '抖音火山']

NOISE_VERBS = ['进行', '需要', '能够', '良好', '具有', '具备', '提供', '包括', '基于',
               '通过', '根据', '按照', '符合', '满足', '达到', '实现', '完成', '做好',
               '做好', '努力', '积极', '主动', '认真', '仔细', '耐心', '细心', '负责',
               '热爱', '喜欢', '感兴趣', '愿意', '希望', '期望', '目标', '方向', '计划']

def is_pure_number(word):
    return word.replace('.', '', 1).isdigit()

def is_single_letter(word):
    return len(word) == 1 and word.isalpha()

def is_city(word):
    return word in CITIES

def is_stopword(word):
    return word in STOPWORDS

def is_company(word):
    return word in COMPANY_TERMS

def is_noise_verb(word):
    return word in NOISE_VERBS

def should_exclude(word):
    reasons = []
    
    if is_pure_number(word):
        reasons.append("纯数字")
    if is_single_letter(word):
        reasons.append("单个字母")
    if is_city(word):
        reasons.append("城市名")
    if is_stopword(word):
        reasons.append("无意义词")
    if is_company(word):
        reasons.append("公司相关词")
    if is_noise_verb(word):
        reasons.append("无关动词/形容词")
    
    return reasons

def main():
    print("="*60)
    print("技能白名单生成工具")
    print("="*60)
    print()
    
    keywords_with_count = []
    removed_terms = []
    
    try:
        with open(INPUT_FILE, 'r', encoding='utf-8-sig') as f:
            reader = csv.DictReader(f)
            for row in reader:
                word = row['关键词'].lower()
                count = int(row['出现次数'])
                keywords_with_count.append((word, count))
        
        print(f"✓ 读取到 {len(keywords_with_count)} 个关键词")
        print()
        
        whitelist = []
        
        for word, count in keywords_with_count:
            exclude_reasons = should_exclude(word)
            if exclude_reasons:
                reason_str = "; ".join(exclude_reasons)
                removed_terms.append((word, count, reason_str))
            else:
                whitelist.append((word, count))
        
        # 按频率降序排序
        whitelist.sort(key=lambda x: (-x[1], x[0]))
        
        print(f"白名单统计：")
        print(f"✓ 保留技能词: {len(whitelist)} 个")
        print(f"✗ 过滤掉的词: {len(removed_terms)} 个")
        print()
        
        print("前30个白名单技能：")
        print("-"*60)
        for idx, (word, count) in enumerate(whitelist[:30], 1):
            print(f"{idx:<4} {word:<20} {count:<8}")
        
        print()
        print(f"正在写入白名单文件 {WHITELIST_FILE}...")
        with open(WHITELIST_FILE, 'w', encoding='utf-8') as f:
            for word, _ in whitelist:
                f.write(word + '\n')
        print("✓ 白名单文件保存成功！")
        
        print()
        print(f"正在写入被过滤词文件 {REMOVED_FILE}...")
        with open(REMOVED_FILE, 'w', encoding='utf-8') as f:
            f.write(f"{'关键词':<20} {'出现次数':<10} {'过滤原因'}\n")
            f.write("-"*60 + '\n')
            for word, count, reason in removed_terms:
                f.write(f"{word:<20} {count:<10} {reason}\n")
        print("✓ 被过滤词文件保存成功！")
        
        print()
        print("="*60)
        print("白名单生成完成！")
        print("="*60)
        
    except FileNotFoundError:
        print(f"✗ 错误: 找不到文件 {INPUT_FILE}")
        print("请先运行 analyze_keywords.py 生成关键词文件！")
    except Exception as e:
        print(f"✗ 错误: {e}")
        import traceback
        traceback.print_exc()

if __name__ == '__main__':
    main()
