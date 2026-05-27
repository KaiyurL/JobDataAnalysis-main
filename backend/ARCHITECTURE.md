# 后端结构说明（Spring Boot）

## 分层约定

- controller：HTTP 接口层，仅做参数接收、认证上下文获取、调用 service、返回 Result
- service：业务编排层，聚合多个 mapper/service、实现业务流程
- mapper：MyBatis-Plus 数据访问层
- entity：数据库实体（与表结构对应）
- dto：请求/响应/统计 DTO（对外接口的数据模型）
- config：Spring 配置、安全、MyBatis 配置、初始化逻辑
- ai：统一推荐源（①语义参考 + ②数据库候选检索 + ③大模型生成建议）
- util：通用工具（无业务语义）
- constant：常量集中管理
- exception：统一异常模型与全局异常处理

## 关键模块

### AI 统一推荐源（com.jobdata.ai.*）

- 聊天接口：
  - [AgentController](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/AgentController.java)（`/api/agent/chat` / `/api/agent/chat/stream`）
- 核心编排：
  - [AgentChatService](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/AgentChatService.java)
    - ① buildCitations：语义参考（向量库检索）仅用于推理
    - ② fetchCandidates：数据库候选（job_search 工具）
    - selectRecommendations：大模型精排，仅从候选中选择最终推荐岗位
    - generateFinalReply：综合①②+内置知识输出最终建议
- 工具（Tool Calling）：
  - [JobTools](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/tools/JobTools.java)（job_search）
  - [UserTools](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/tools/UserTools.java)（user_*）
- RAG 索引：
  - [JobRagIndexer](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/rag/JobRagIndexer.java)
  - [RagAdminController](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/rag/RagAdminController.java)（`/api/rag/reindex/jobs`）

### 招聘岗位与统计（com.jobdata.controller / service / mapper / entity）

- 岗位查询与统计：
  - [JobInfoController](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/JobInfoController.java)
  - [JobInfo51JobController](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/JobInfo51JobController.java)
- 用户与登录：
  - [AuthController](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/AuthController.java)
  - [UserController](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/UserController.java)

## 约束与实践

- controller 不直接访问 mapper
- service 负责事务/编排，mapper 仅做 CRUD
- 对外返回统一使用 [Result](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/Result.java)
- 异常统一由 [GlobalExceptionHandler](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/exception/GlobalExceptionHandler.java) 转换为 Result

