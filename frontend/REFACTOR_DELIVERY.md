# 前端全站重构交付说明

## 1. 重构范围

- 覆盖目录：frontend/src 下的全局样式、应用布局、路由与 views 全部页面
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
- CompanyInsight / SalaryPredict：统一错误提示与图表配色读取主题色
- JobMatch：匹配分数颜色从硬编码改为读取主题语义色

## 6. 性能优化

### 6.1 路由懒加载（代码分割）

落地文件：
- frontend/src/router/index.js

效果：
- views 全部改为动态 import()，将大页面与大依赖延后加载
- vite build 产物由单一超大 chunk 拆分为多个页面 chunk（仍有 echarts/pdf 相关较大 chunk，属依赖体积因素）

## 7. 页面重构清单（已覆盖）

- App.vue：全局导航与内容壳统一，过渡动画统一为 jd-fade
- Login.vue：全新登录页布局与视觉统一
- Dashboard.vue：主题色统一、图表配色统一、卡片/统计区样式收敛
- JobAnalysisLayout.vue：子导航收敛为 jd-seg 体系
- JobAnalysis.vue：页面头/布局/颜色统一，筛选区响应式优化
- SkillAnalysis.vue：统一 header + 图表容器高度体系 + 主题色驱动图表
- SalaryPredict.vue：统一 header + 结果卡视觉与语义色
- CompanyInsight.vue：统一 header + 主题色驱动图表
- DataManagement.vue：统一 header + 色彩/边框/日志区风格统一
- JobMatch.vue：统一 header + 主题色替换关键硬编码颜色 + 按钮/布局局部原子化

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
  - 将 echarts/wordcloud 拆为按页面 import（仅 Dashboard/SkillAnalysis 使用时加载）
- Safari 真机测试需要在目标环境进行（本地构建与预览已通过） 

