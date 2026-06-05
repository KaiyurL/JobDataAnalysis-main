# 后端结构说明（Spring Boot）

本文用于快速了解后端代码布局、各文件职责，以及核心数据流（接口 → 业务编排 → 数据访问 → 返回）。

## 入口与配置文件

- 启动入口：[JobDataAnalysisApplication.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/JobDataAnalysisApplication.java)
- Spring 配置：[application.yml](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/resources/application.yml)
- Maven 依赖：[pom.xml](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/pom.xml)

## 项目结构（按包）

代码根目录：`backend/src/main/java/com/jobdata`

### controller（HTTP 接口层）

仅做：参数接收 → 从认证上下文取 userId → 调用 service → 返回 [Result.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/Result.java)。

- 认证与用户：
  - [AuthController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/AuthController.java)：登录/注册，签发 JWT
  - [UserActivityController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/UserActivityController.java)：用户收藏、浏览历史
- 职位与统计：
  - [JobInfoController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/JobInfoController.java)：BOSS 职位分页与统计
  - [JobInfo51JobController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/JobInfo51JobController.java)：51Job 职位分页与统计
- 数据更新与流水线：
  - [DataManageController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/DataManageController.java)：爬虫更新流程控制与日志清理
  - [PipelineController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/PipelineController.java)：触发离线 pipeline、查询状态/产物、下载文件
- 配置：
  - [ConfigController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/controller/ConfigController.java)：crawler/config.json 读取与更新

### service（业务编排层）

封装业务流程，可组合多个 mapper/service；复杂场景可在此处加事务。

- 接口定义（主要用于 controller 调用）：
  - [JobInfoService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/JobInfoService.java)
  - [JobInfo51JobService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/JobInfo51JobService.java)
  - [UserService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/UserService.java)
  - [UserFavoriteJobService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/UserFavoriteJobService.java)
  - [UserJobHistoryService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/UserJobHistoryService.java)
  - [ConfigService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/ConfigService.java)
  - [DataManageService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/DataManageService.java)
  - [PipelineService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/PipelineService.java)
- 默认实现（MyBatis-Plus ServiceImpl 为主）：
  - [JobInfoServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/JobInfoServiceImpl.java)：职位查询与统计聚合
  - [JobInfo51JobServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/JobInfo51JobServiceImpl.java)
  - [UserServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/UserServiceImpl.java)
  - [UserFavoriteJobServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/UserFavoriteJobServiceImpl.java)
  - [UserJobHistoryServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/UserJobHistoryServiceImpl.java)
  - [ConfigServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/ConfigServiceImpl.java)：定位 crawler 目录并读写 config.json
  - [DataManageServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/DataManageServiceImpl.java)：启动 spider.py、日志采集、登录确认
  - [PipelineServiceImpl.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/service/impl/PipelineServiceImpl.java)：执行 nlp_job_pipeline.py、缓存与产物管理

### mapper（数据访问层，MyBatis-Plus）

约定：mapper 仅做 CRUD + 少量“直连 SQL”（例如统计指纹），不承载业务规则。

- 职位：
  - [JobInfoMapper.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/mapper/JobInfoMapper.java)：job_info CRUD + 指纹查询
  - [JobInfo51JobMapper.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/mapper/JobInfo51JobMapper.java)：job_info_51job CRUD + 指纹查询
- 用户：
  - [UserMapper.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/mapper/UserMapper.java)
  - [UserFavoriteJobMapper.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/mapper/UserFavoriteJobMapper.java)
  - [UserJobHistoryMapper.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/mapper/UserJobHistoryMapper.java)

### entity（数据库实体）

对应数据库表字段，通常只承载数据本身。

- 职位表：
  - [JobInfo.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/entity/JobInfo.java)（job_info）
  - [JobInfo51Job.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/entity/JobInfo51Job.java)（job_info_51job）
- 用户域：
  - [User.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/entity/User.java)（users）
  - [UserFavoriteJob.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/entity/UserFavoriteJob.java)（user_favorite_job）
  - [UserJobHistory.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/entity/UserJobHistory.java)（user_job_history）

### dto（接口数据模型）

用于接口请求/响应/统计聚合结果。

- 通用返回：[Result.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/Result.java)
- 登录：
  - [LoginRequest.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/LoginRequest.java)
  - [LoginResponse.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/LoginResponse.java)
- 统计 DTO：
  - [CitySalaryDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/CitySalaryDTO.java)
  - [EducationSalaryDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/EducationSalaryDTO.java)
  - [ExperienceSalaryDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/ExperienceSalaryDTO.java)
  - [KeywordDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/KeywordDTO.java)
  - [IndustryCountDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/IndustryCountDTO.java)
  - [CompanyHotDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/CompanyHotDTO.java)
  - [CompanySalaryDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/CompanySalaryDTO.java)
  - [CompanySizeDTO.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/CompanySizeDTO.java)
- AI 对话请求：
  - [AiChatRequest.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/dto/AiChatRequest.java)

### config / constant / util / exception（横切能力）

- 安全与鉴权：
  - [SecurityConfig.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/config/SecurityConfig.java)：SecurityFilterChain、CORS、PasswordEncoder
  - [JwtAuthenticationFilter.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/config/JwtAuthenticationFilter.java)：从 Authorization Bearer 解析 JWT 并写入 SecurityContext
  - [JwtUtil.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/util/JwtUtil.java)：JWT 生成/解析/校验
  - [SecurityConstants.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/constant/SecurityConstants.java)：Header、角色常量
- MyBatis-Plus：
  - [MybatisPlusConfig.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/config/MybatisPlusConfig.java)：注册拦截器（含分页方言）
- 初始化与异常：
  - [DatabaseInit.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/config/DatabaseInit.java)：启动时初始化默认管理员账号（测试用途）
  - [BizException.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/exception/BizException.java)：业务异常模型
  - [GlobalExceptionHandler.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/exception/GlobalExceptionHandler.java)：统一转为 Result

## AI 模块（com.jobdata.ai.*）

目标：在“真实岗位数据”的约束下，提供可解释、可检索、可工具调用的 AI 对话推荐。

- 对外接口：
  - [AgentController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/controller/AgentController.java)（`/api/agent/chat`、`/api/agent/chat/stream`）
  - [RagAdminController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/controller/RagAdminController.java)（`/api/rag/reindex/jobs`）
- 简历与画像：
  - [ResumeController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/controller/ResumeController.java)（`/api/resume/parse`）
  - [UserProfileController.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/controller/UserProfileController.java)（`/api/user/profile`）
- 核心编排：
  - [AgentChatService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/service/AgentChatService.java)：聚合画像、向量检索、候选召回、精排与最终回复
- AI Service：
  - [ResumeService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/service/ResumeService.java)
  - [UserProfileService.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/service/UserProfileService.java)
- 工具（Tool Calling）：
  - [JobTools.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/tools/JobTools.java)：job_search（数据库检索候选）
  - [UserTools.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/tools/UserTools.java)：user_*（画像/收藏/历史）
  - [JobToolResultStore.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/tools/JobToolResultStore.java)：暂存岗位卡片（单次请求内）
- RAG 索引：
  - [JobRagIndexer.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/rag/JobRagIndexer.java)：将岗位构造成向量文档写入向量库
  - [RagReindexJobManager.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/rag/RagReindexJobManager.java)：重建索引任务编排（含数据清洗与进度管理）
- 线程上下文：
  - [UserContextHolder.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/main/java/com/jobdata/ai/common/UserContextHolder.java)：ThreadLocal 保存 userId（供 user_* 工具读取）

## 测试与报告

- API 冒烟测试（生成报告）：[ApiFullSmokeTest.java](file:///d:/课程文件/智能应用/project/JobDataAnalysis-main/JobDataAnalysis-main/backend/src/test/java/com/jobdata/api/ApiFullSmokeTest.java)
- 测试报告输出目录：`backend/test-report/`

## 约束与实践

- controller 不直接访问 mapper；controller → service → mapper 逐层调用
- service 负责业务编排与事务边界；mapper 仅做数据访问
- 对外返回统一使用 Result；异常统一由 GlobalExceptionHandler 转换为 Result

## 当前项目结构与文件作用

### 1) 工程根目录（backend/）

```
backend/
  pom.xml                    # Maven 依赖与构建配置（Spring Boot + MyBatis-Plus + Spring AI）
  ARCHITECTURE.md            # 本文档：后端结构与分层说明

  src/
    main/
      java/
        com/jobdata/         # 业务源码（见 2）
      resources/
        application.yml      # Spring Boot 配置（数据源/JWT/端口/Spring AI 等）
    test/
      java/
        com/jobdata/api/
          ApiFullSmokeTest.java  # API 冒烟测试：扫描 controller 并生成测试报告

  test-report/               # ApiFullSmokeTest 输出目录（运行测试后生成）
```

### 2) 源码目录（backend/src/main/java/com/jobdata）

```
com/jobdata/
  JobDataAnalysisApplication.java   # 启动入口（SpringBootApplication + MapperScan）

  controller/                       # HTTP 接口层：参数接收/鉴权上下文取 userId/返回 Result
    AuthController.java             # 登录/注册，签发 JWT
    UserActivityController.java     # 用户收藏、浏览历史
    JobInfoController.java          # BOSS 职位分页与统计
    JobInfo51JobController.java     # 51Job 职位分页与统计
    ConfigController.java           # crawler/config.json 读取与更新
    DataManageController.java       # 爬虫更新流程控制、日志清理、登录确认
    PipelineController.java         # 离线 pipeline 触发、状态/产物查询、产物文件下载

  service/                          # 业务编排层：封装业务流程/可组合多个 service/mapper/可加事务
    *.java                          # service 接口定义（供 controller 调用）
    impl/                           # service 实现（多数基于 MyBatis-Plus ServiceImpl）
      JobInfoServiceImpl.java       # 职位查询与统计聚合
      JobInfo51JobServiceImpl.java  # 51Job 职位查询与统计聚合
      DataManageServiceImpl.java    # 运行 spider.py，日志采集，登录确认信号
      PipelineServiceImpl.java      # 运行 nlp_job_pipeline.py，缓存与产物管理
      ResumeServiceImpl.java        # 文件解析/OCR/调用模型抽取 JSON
      ConfigServiceImpl.java        # 定位 crawler 目录并读写 config.json
      UserServiceImpl.java          # 用户查询/创建、密码加密、默认角色

  mapper/                           # 数据访问层（MyBatis-Plus Mapper）：CRUD + 少量直连 SQL
    JobInfoMapper.java              # job_info CRUD + 数据指纹（count/max(created_at)）
    JobInfo51JobMapper.java         # job_info_51job CRUD + 数据指纹
    User*Mapper.java                # 用户相关表 CRUD

  entity/                           # 数据库实体（TableName/TableId）
    JobInfo.java                    # job_info
    JobInfo51Job.java               # job_info_51job
    User.java                       # users
    UserFavoriteJob.java            # user_favorite_job
    UserJobHistory.java             # user_job_history

  dto/                              # 接口数据模型（请求/响应/统计 DTO）
    Result.java                     # 统一返回结构（code/message/data）
    LoginRequest.java               # 登录/注册请求
    LoginResponse.java              # 登录响应（token + userInfo）
    *DTO.java                       # 统计聚合 DTO（城市/学历/经验/行业/公司等）
    AiChatRequest.java              # AI 对话请求（profile/message/history）

  config/                           # Spring 配置类（Security/MyBatisPlus/初始化等）
    SecurityConfig.java             # Spring Security 配置（JWT 无状态鉴权 + CORS）
    JwtAuthenticationFilter.java    # JWT 解析与认证写入上下文
    MybatisPlusConfig.java          # MyBatis-Plus 拦截器（分页方言等）
    DatabaseInit.java               # 启动时初始化默认管理员账号（测试用途）

  constant/                         # 常量定义
    SecurityConstants.java          # Header、Bearer 前缀、角色常量

  util/                             # 工具类
    JwtUtil.java                    # JWT 生成/解析/校验

  exception/                        # 异常模型与全局异常处理
    BizException.java               # 业务异常（含 code）
    GlobalExceptionHandler.java     # 统一转为 Result

  ai/                               # AI 模块（Spring AI + RAG + Tool Calling）
    common/
      EmbeddingDimensionValidator.java # 向量维度配置校验
      UserContextHolder.java          # ThreadLocal 保存 userId（供 user_* 工具读取）
    controller/
      AgentController.java          # /api/agent/chat 与 /api/agent/chat/stream
      RagAdminController.java       # /api/rag/reindex/jobs（重建索引前会清洗 job_url 重复数据）
      ResumeController.java         # /api/resume/parse（上传简历解析）
      UserProfileController.java    # /api/user/profile（用户画像读写）
    dto/
      AgentChatResponse.java        # AI 对话响应
      AgentStreamEvent.java         # SSE 流式事件模型
    entity/
      UserProfile.java              # user_profile（画像表）
    mapper/
      UserProfileMapper.java        # user_profile CRUD
    service/
      AgentChatService.java         # 画像聚合、向量检索、候选召回、精排、生成最终回复
      ResumeService.java            # 简历文件解析与模型抽取
      UserProfileService.java       # 用户画像读写
      impl/
        ResumeServiceImpl.java      # 简历解析实现
        UserProfileServiceImpl.java # 用户画像实现
    tools/
      JobTools.java                 # job_search：从真实数据库筛选岗位
      UserTools.java                # user_*：读取/更新用户画像与收藏/历史
      JobToolResultStore.java       # 暂存岗位卡片（单次请求内）
    rag/
      JobRagIndexer.java            # 将岗位构造成向量文档写入向量库
      RagReindexJobManager.java     # 重建索引任务编排
```

### 3) 分层规则（约定）

- 接口层 controller：只做请求/响应适配，不写业务规则；需要用户身份时从 Authentication 取 userId。
- 业务层 service：负责业务编排与事务边界；跨表/跨模块逻辑优先放 service。
- 数据层 mapper/entity：mapper 做数据访问与少量直连 SQL；entity 仅承载数据结构。
- 统一返回与异常：所有接口返回 Result；异常由 GlobalExceptionHandler 统一转换为 Result。
- AI 模块约束：推荐与对话必须基于真实岗位数据（job_search/向量检索），不凭空编造岗位。
