# 前端全站重构交付说明

## 1. 重构范围

- 覆盖目录：frontend/src 下的全局样式、应用布局、路由与页面实现（pages + features）
- 覆盖维度：样式体系（原子化 utilities + tokens）、配色系统（品牌色/语义色/Element Plus 主题覆盖）、核心交互（加载/错误/反馈/可访问性）

## 2. 设计方向

- 视觉基调：偏“数据驾驶舱 + 纸感背景”的轻质玻璃拟态（低透明、低饱和、强调层级与信息密度）
- 关键词：克制的装饰、明确的信息层级、可读性优先、动效少而准

## 3. 样式体系（原子化 + 全局规范）

落地文件：
- frontend/src/style.css

### 3.1 全局 reset 与可访问性焦点

- 统一 box-sizing
- 统一 body 字体与背景
- 使用 :focus-visible 提供键盘可见焦点环，避免“只能鼠标才能用”的交互障碍

### 3.2 设计 tokens（颜色/字体/圆角/阴影/间距）

颜色（核心）：
- Ink: #0b1220 / #334155 / #64748b
- Paper: #f7f2ea
- Primary: #0f766e（主色）
- Accent: #ff6b4a（强调色）
- Info/Success/Warning/Danger：#2563eb / #16a34a / #f59e0b / #dc2626

字体：
- Body: IBM Plex Sans
- Display: Fraunces

圆角/阴影/间距：
- radius/shadow/space 统一为 CSS 变量，避免“每页都写一套”的维护成本

### 3.3 原子化 utilities（示例）

- 布局：u-container / u-stack / u-row / u-grid-2/3/4
- 间距：u-gap-* / u-mt-*
- 文本：u-title / u-h1/u-h2/u-text / u-muted/u-subtle/u-primary
- 宽度：u-w-full

## 4. 配色重构（含 Element Plus 主题统一）

落地文件：
- frontend/src/style.css（通过 CSS 变量覆盖 Element Plus 主题）

关键覆盖：
- --el-color-primary / --el-text-color-* / --el-border-radius-base 等

可访问性（WCAG 2.1 AA）
- 主文本（Ink）与背景（Paper/Surface）对比度满足常规正文阅读
- Primary 按钮白字与主色背景对比度满足按钮文本可读性
- focus-visible 可见焦点与足够的 outline offset，便于键盘导航

## 5. 交互重构（加载/错误/反馈）

通用策略：
- 所有数据请求失败优先展示后端 message 或网络错误 message，避免只提示“失败”
- 对需要等待的操作（匹配岗位、预测、统计拉取）保留 loading 状态

页面落地点（举例）：
- Login：失败提示从“统一文案”提升为优先展示后端 message
- Dashboard / CompanyInsight：统一错误提示与图表配色读取主题色
- JobMatch：匹配分数颜色从硬编码改为读取主题语义色

## 6. 性能优化

### 6.1 路由懒加载（代码分割）

落地文件：
- frontend/src/app/routes.js
- frontend/src/app/router/index.js
- frontend/src/pages/*Page.vue

效果：
- pages 路由入口全部使用动态 import()，将大页面与大依赖延后加载
- vite build 产物由单一超大 chunk 拆分为多个页面 chunk（仍有 echarts/pdf 相关较大 chunk，属依赖体积因素）

## 7. 页面重构清单（已覆盖）

- App.vue：全局导航与内容壳统一，过渡动画统一为 jd-fade
- Login（features/auth/LoginView.vue）：全新登录页布局与视觉统一
- Dashboard（features/dashboard/DashboardView.vue）：主题色统一、图表配色统一、卡片/统计区样式收敛
- JobAnalysisLayout（features/job-analysis/JobAnalysisLayoutView.vue）：子导航收敛为 jd-seg 体系
- JobAnalysis（features/job-analysis/JobAnalysisView.vue）：页面头/布局/颜色统一，筛选区响应式优化
- CompanyInsight（features/job-analysis/CompanyInsightView.vue）：统一 header + 主题色驱动图表
- DataManagement（features/data-management/DataManagementView.vue）：统一 header + 色彩/边框/日志区风格统一
- JobMatch（features/job-match/JobMatchView.vue）：统一 header + 主题色替换关键硬编码颜色 + 按钮/布局局部原子化

## 8. 测试与验证

已执行：
- npm run build（通过）
- npm run preview（可启动）
- 静态检查：IDE diagnostics 为 0

建议的跨浏览器检查清单（Chrome/Firefox/Safari/Edge）：
- 登录页输入框/按钮聚焦状态（Tab 键）
- Dashboard 图表渲染与 resize（窗口缩放）
- JobMatch 上传简历、匹配按钮 loading、卡片弹窗滚动
- 表格横向滚动（小屏）与分页

## 9. 已知限制与后续建议

- 仍有较大 chunk（echarts、导出 PDF 相关依赖）；如需进一步压缩首屏：
  - 将导出 PDF 功能改为按需 import（点击导出时才加载）
  - 将 echarts/wordcloud 拆为按页面 import（仅 Dashboard / CompanyInsight 使用时加载）
- Safari 真机测试需要在目标环境进行（本地构建与预览已通过） 

## 10. 当前项目结构与文件作用

### 10.1 工程根目录（frontend/）

```
frontend/
  src/                       # 业务源码（见 10.2）
  index.html                 # Vite HTML 模板
  vite.config.js             # Vite 配置（含 /api 代理与 env 驱动）
  .env.development           # 开发环境变量（如 VITE_API_TARGET）
  package.json               # 依赖与脚本（dev/build/lint/format）
  eslint.config.js           # ESLint（flat config）
  .prettierrc.json           # Prettier 格式化规则
  .prettierignore            # Prettier 忽略项（dist 等）
  REFACTOR_DELIVERY.md       # 本交付说明
```

### 10.2 源码目录（frontend/src）

```
src/
  main.js                    # 入口：创建并挂载应用（最小化）
  App.vue                    # 全局壳：导航/菜单/用户入口/keep-alive
  style.css                  # 全局 tokens + utilities + Element Plus 主题覆盖

  app/                       # 应用装配与路由/导航配置（“装配层”）
    createApp.js             # 装配 VueApp + ElementPlus + router + 全局样式
    navConfig.js             # 单一来源导航配置（菜单 + 权限字段）
    routes.js                # 路由表定义（pages 懒加载 + meta 派生）
    router/index.js          # router 实例与全局守卫（鉴权/角色拦截）

  pages/                     # 路由页面壳（Route Entry）：*Page.vue
                             # 约定：Page 只负责承载/组合 features 的 View

  features/                  # 业务功能域（Feature-First）
    auth/                    # 登录
    dashboard/               # 数据仪表盘（图表/统计/深度分析）
    job-analysis/            # 岗位分析（含公司洞察等子页）
    job-match/               # 智能助手/岗位匹配（聊天/简历/详情弹窗等）
    data-management/         # 系统设置/数据管理
                             # 约定：feature 内可包含 components/ composables/

  services/                  # 服务层（API 与请求封装）
    http.js                  # Axios 实例 + token 注入 + 401 处理 + 统一解包
    modules/                 # 按领域拆分的 API 模块（auth/jobs/user/config/pipeline/rag/resume/...）
    index.js                 # 按领域命名空间聚合导出（*Api）
  api.js                     # 兼容聚合层（历史默认导出，建议新代码不再引用）

  shared/                    # 横切能力（跨 feature 复用）
    authStorage.js           # token/userInfo 持久化与清理策略
    theme.js                 # 主题 token 读取（cssVar/baseTheme）

  utils/                     # 通用工具
    exportPdf.js             # 多页 PDF 导出工具（html2canvas + jsPDF）

  components/                # 全局通用组件（与具体 feature 无强绑定）
    AppNavMenu.vue           # 左侧导航菜单（读取 navConfig + router）

  composables/               # 全局通用组合式逻辑（避免放业务域逻辑）
    useUserDataStore.js      # 跨页面用户数据（收藏/历史等）管理
```

### 10.3 分层规则（约定）

- 路由入口统一在 pages：`routes.js` 只懒加载 `pages/*Page.vue`，避免路由直接指向 feature 内部组件。
- 业务逻辑归属 features：页面私有组件与 composables 放到对应 feature 内，避免在 `src/components` 与 `src/composables` 混放。
- 数据访问走 services：页面/feature 不直接处理 `res.data.code/message`，统一使用 `services/http.js` 的解包能力与 `modules/*` 的 `*Data` 方法。
- 横切能力走 shared：鉴权存储、主题 token 等统一来源，避免各页面重复实现导致口径漂移。
