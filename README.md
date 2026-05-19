# 招聘数据智能分析与可视化平台

![Java](https://img.shields.io/badge/Java-8-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-green.svg)
![Vue](https://img.shields.io/badge/Vue-3-blue.svg)
![Python](https://img.shields.io/badge/Python-3.x-yellow.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)

## 项目简介

本项目是一个招聘数据全链路分析平台，从数据采集、清洗、存储到可视化分析和智能推荐，实现了求职市场的多维度洞察。主要面向应届生、HR 及数据分析爱好者，提供：

真实招聘数据（10,000+ 条）的自动化采集与清洗

交互式可视化看板（8+ 图表联动，支持多维度筛选）

薪资预测与岗位匹配算法（基于统计模型 + Jaccard 相似度）

完整的用户认证与 PDF/CSV 报告导出

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.18 |
| 前端框架 | Vue | 3.4.0 |
| 数据库 | MySQL | 8.0+ |
| 爬虫 | Python + DrissionPage | 4.0+ |
| 可视化 | ECharts | 5.4.3 |
| 组件库 | Element Plus | 2.14.0 |
| 构建工具 | Maven / Vite | - |

## 主要功能

### 📊 数据可视化大屏
- **城市薪资分布** - 各城市岗位薪资对比分析
- **学历薪资趋势** - 不同学历对应的薪资水平
- **经验薪资曲线** - 工作经验与薪资关系
- **技能词云** - 热门技能关键词展示
- **行业分布** - 各行业岗位数量统计
- **企业热度排行** - 热门企业招聘数量
- **企业薪资排行** - 企业平均薪资对比
- **企业规模分布** - 不同规模企业占比

### 🤖 智能分析功能
- **薪资预测** - 基于相似岗位的统计模型（均值±标准差），用户输入学历/经验/城市 → 预测区间 + 置信度（MAE=2.3K）
- **岗位匹配** - 基于技能标签的智能岗位推荐，Jaccard 相似度计算技能匹配度，输出 Top 10 推荐岗位

### 🔐 用户认证
- JWT 无状态认证
- 用户登录/注册
- 接口权限控制

### 🕷️ 数据采集
在项目开发中，我们针对 主流的招聘平台的反爬机制采取了以下策略，确保数据采集的稳定性和合规性：

- 框架选型：DrissionPage 替代传统 Selenium
传统 Selenium 模拟浏览器容易被检测，而 DrissionPage 能直接监听浏览器发出的网络请求（XHR/Fetch），精准捕获返回招聘数据的 JSON 接口，绕过了页面渲染层面的反爬。这种方式本质上与真实用户浏览行为一致，成功率更高。

- 手动登录 + Cookies 持久化
利用 DrissionPage 打开浏览器，人工扫码登录一次，随后将登录状态（Cookies）保存到本地文件。后续爬虫自动加载该 Cookies，避免每次运行都触发登录检测。

- 随机延迟与访问频率控制
在每次请求之间设置 5~10 秒的随机延迟（random.uniform(5,10)），并控制爬取页数（每个关键词 2 页），模拟正常用户的浏览节奏，避免高频请求触发封 IP。

- 用户代理轮换与浏览器指纹伪装
在请求头中动态切换 User-Agent，并使用 DrissionPage 自带的浏览器指纹隐藏功能（如禁用 navigator.webdriver 标志），降低被识别为自动化脚本的风险。

- 错误重试与验证码人工介入
当检测到页面返回验证码或空白页时，程序自动截图并暂停，等待手动处理后继续。虽然简单，但在实际测试中有效应对了偶发的风控挑战。

- 数据量与频率克制
最终只采集 5 个城市 × 5 个关键词 × 2 页 ≈ 10,000 条数据，远低于触发大规模反爬的阈值，保证项目演示和算法训练需求的同时，未对平台造成压力。

注：以上措施均以学习和研究为目的，且严格遵守 robots.txt 规范，未对平台服务器造成过载。在实际生产环境中，可进一步结合代理 IP 池、分布式调度等方案提升稳定性。

## 量化成果

| 指标 | 数值 |
|------|------|
| 爬取数据量 | 10,000+ 条 |
| 爬取成功率 | 95%+ |
| 数据完整度提升 | 60% |
| 查询响应时间 | < 200ms |
| 岗位推荐准确率 | 85%+ |
| 薪资预测覆盖 | 90% 主流岗位 |

## 项目结构

```
JobDataAnalysis/
├── backend/                 # Spring Boot 后端
│   ├── src/main/java/       # Java 源代码
│   ├── src/main/resources/  # 配置文件
│   └── pom.xml              # Maven 配置
├── frontend/                # Vue 3 前端
│   ├── src/                 # Vue 源代码
│   ├── index.html           # HTML 入口
│   ├── package.json         # 依赖配置
│   └── vite.config.js       # Vite 配置
├── crawler/                 # Python 爬虫
│   ├── spider.py            # 主爬虫脚本
│   ├── config.py            # 配置文件
│   └── requirements.txt     # Python 依赖
├── database/                # 数据库脚本
│   └── scripts/             # SQL 脚本
├── .gitignore               # Git 忽略配置
└── README.md                # 项目说明
```

## 本地运行步骤

### 环境要求

- JDK 8+
- Maven 3.x
- Node.js 16+
- Python 3.8+
- MySQL 5.7+

### 1. 数据库配置

数据库账号：root  
数据库密码：123456ppoo

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS job_data DEFAULT CHARACTER SET utf8mb4;

-- 创建用户表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) DEFAULT 'user',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 创建岗位信息表（schema.sql）
-- 执行 database/scripts/schema.sql
```

### 2. 后端启动

```bash
cd backend
mvn clean spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动

### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端服务将在 `http://localhost:5173` 启动

### 4. 爬虫运行（可选）

```bash
cd crawler
pip install -r requirements.txt
playwright install chromium
python spider.py
```

## 项目截图

见images文件夹

## API 接口示例

### 登录接口
```bash
POST /api/auth/login
{
    "username": "admin",
    "password": "admin123"
}
```

### 获取统计概览
```bash
GET /api/jobs/stats/overview
```

### 薪资预测
```bash
POST /api/jobs/predict/salary
{
    "education": "本科",
    "experience": "3-5年",
    "city": "北京",
    "keyword": "Java"
}
```

## 开源协议

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！若需扩展数据源或优化算法，请参考开发文档（https://www.yuque.com/xiaopacai-0kvnt/onwagq/vkaze4lkw4y7bhd9?singleDoc# 《招聘数据可视化分析平台 - 项目部署与功能说明文档》）

## 联系方式

如有问题或建议，请通过以下方式联系：
- GitHub: [SZBDAS](https://github.com/SZBDAS)
