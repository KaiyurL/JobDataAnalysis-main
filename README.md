# 招聘数据智能分析与可视化平台

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)
![Vue](https://img.shields.io/badge/Vue-3-blue.svg)
![Python](https://img.shields.io/badge/Python-3.x-yellow.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)

## 项目简介

本项目是一个招聘数据全链路分析平台，从数据采集、清洗、存储到可视化分析和智能推荐，实现求职市场的多维度洞察。

支持数据源：
- BOSS直聘
- 前程无忧（51job）

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.12 |
| 前端框架 | Vue | 3.4.0 |
| 数据库 | PostgreSQL + pgvector | 15+ |
| 爬虫 | Python + DrissionPage | 4.0+ |
| 可视化 | ECharts | 5.4.3 |
| 组件库 | Element Plus | 2.14.0 |
| 构建工具 | Maven / Vite | - |

## 主要功能

### 📊 数据可视化
- 城市薪资分布、学历薪资趋势、经验薪资曲线、技能词云、行业分布
- 企业热度排行、企业薪资排行、企业规模分布
- 多数据源统一口径统计（可视化统计会算上前程无忧数据）

### 🤖 智能分析
- 薪资预测：基于相似岗位的统计模型（均值±标准差）
- 统一推荐源：语义参考（向量检索）+ 数据库候选检索 + 大模型精排/总结输出

### ️ 数据采集
- 支持 BOSS直聘 / 前程无忧 两个平台爬取，前程无忧数据独立入表 `job_info_51job`
- 爬取配置可在前端「数据管理」页面编辑并保存
- 后端触发爬虫并在前端显示运行日志/开始结束时间

### 🧾 数据字段
核心字段包括：
- 工作介绍 `job_desc`
- 公司福利 `company_welfare`
- 招聘路径 `job_url`

## 项目结构

```
JobDataAnalysis/
├── backend/                  # Spring Boot 后端
├── frontend/                 # Vue 3 前端
├── crawler/                  # Python 爬虫（spider.py）
└── README.md                 # 项目说明
```

## 本地运行步骤

### 环境要求
- JDK 21（后端 `backend/pom.xml` 指定 `java.version=21`）
- Maven 3.x
- Node.js 18+
- Python 3.8+
- PostgreSQL 15+（需要安装 pgvector 扩展）

### 1. 数据库配置（PostgreSQL）

后端默认使用 PostgreSQL（含 pgvector 向量库），连接信息在：
- `backend/src/main/resources/application.yml`
- 支持环境变量：`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`

数据库表与向量库结构由 Flyway 迁移脚本管理（`backend/src/main/resources/db/migration`）。

Docker 快速启动（PostgreSQL 15 + pgvector）：

```bash
docker run --name jobdata-pg \
  -e POSTGRES_PASSWORD=123456ppoo \
  -e POSTGRES_DB=job_data \
  -p 5432:5432 \
  -d pgvector/pgvector:pg15
```

### 需要修改的配置（重要）

1) 后端数据库配置（Spring Boot / PostgreSQL）
- 文件：`backend/src/main/resources/application.yml`
- 或通过环境变量配置：`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`

2) 爬虫/NLP 数据库与浏览器配置（Python）
- 文件：`crawler/runtime_config.json`
- 需要确认/修改：
  - `db`（host/port/user/password/database/charset）
  - `browser`（edge_candidates / chrome_candidates 等浏览器路径）

3) 爬虫运行配置（前端「数据管理」保存到本地）
- 文件：`crawler/config.json`（前端「数据管理」页点“保存配置”会写入这里）
- 常用可调整项：
  - `platform`: `boss` / `51job` / `both`
  - `keywords` / `cities`
  - `pages_per_keyword`（Boss 每关键词页数）
  - `pages_per_city_51job`（前程无忧每城市页数）
  - `city_codes_51job`（前程无忧城市编码映射）
  - `delay_min` / `delay_max`（请求间隔，建议保守一点避免风控）
  - `browser`: `auto` / `edge` / `chrome`（选择启动浏览器）
  - 浏览器路径（Windows）：在 `crawler/runtime_config.json` 的 `browser.edge_candidates` / `browser.chrome_candidates` 中配置

4) AI 智能求职助手（支持 OpenAI 兼容模式 + Ollama Embedding 可切换）
- 必需环境变量：
  - `AI_DASHSCOPE_API_KEY`
- 可选环境变量：
  - `AI_BASE_URL`（默认 `https://dashscope.aliyuncs.com/compatible-mode`）
  - `AI_OLLAMA_BASE_URL`（默认 `http://localhost:11434`）

Embedding / 模型如何切换：
- 直接修改后端配置文件：`backend/src/main/resources/application.yml`
  - `spring.ai.model.chat` 固定为 `openai`（用于 DashScope compatible-mode 聊天）
  - `spring.ai.model.embedding` 在 `ollama` / `openai` 间切换（RAG 向量化用哪个 embedding）
  - `spring.ai.openai.embedding.options.model`（例如 `text-embedding-v3`）
  - `spring.ai.ollama.embedding.options.model`（例如 `qwen3-embedding:4b`）
  - `spring.ai.vectorstore.pgvector.dimensions`（例如 `1024`，需与 embedding 输出维度一致）

5) 后端触发爬虫的 Python 解释器
- 若由后端触发爬虫，建议设置环境变量：
  - `JOBDATA_PYTHON`：指向已安装依赖的 `python.exe`（避免系统 python 缺 pip/依赖）

### 2. 后端启动

```bash
cd backend
mvn clean spring-boot:run
```

后端服务：`http://localhost:8080`

首次启动会自动创建/重置测试账号：
- admin / admin123

### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端服务：`http://localhost:3000`

### 4. 爬虫运行

推荐方式（无需命令行交互）：
- 打开前端「数据管理」页面
- 修改配置并点击「保存配置」
- 点击「启动爬虫」，在「运行日志」中查看爬取过程与结束状态

命令行方式：

```bash
cd crawler
pip install -r requirements.txt
python spider.py --platform boss
python spider.py --platform 51job
python spider.py --platform both
```

说明：
- 爬虫会启动浏览器，首次可能需要手动登录/验证
- 若由后端触发爬虫，建议设置环境变量 `JOBDATA_PYTHON` 指向已安装依赖的 python.exe

## 项目截图
见 images 文件夹

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
GET /api/jobs51/stats/overview
```

### 岗位分页

```bash
GET /api/jobs/page
GET /api/jobs51/page
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
欢迎提交 Issue 和 Pull Request！若需扩展数据源或优化算法，请参考开发文档：
https://www.yuque.com/xiaopacai-0kvnt/onwagq/vkaze4lkw4y7bhd9?singleDoc# 《招聘数据可视化分析平台 - 项目部署与功能说明文档》

## 联系方式
- GitHub: [SZBDAS](https://github.com/SZBDAS)
