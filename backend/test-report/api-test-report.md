# API 自动化测试报告

- 总用例数: 34
- 通过: 34
- 失败: 0
- 生成时间: 2026-06-01T14:44:39.189284500

| Method | Path | Status | Content-Type | OK | Latency(ms) | Handler |
|---|---|---:|---|---|---:|---|
|POST |/api/agent/chat |200 |application/json |PASS |10 |AgentController#chat |
|POST |/api/agent/chat/stream |200 | |PASS |2 |AgentController#chatStream |
|POST |/api/auth/login |200 |application/json |PASS |17 |AuthController#login |
|POST |/api/auth/register |200 |application/json |PASS |4 |AuthController#register |
|GET |/api/config |200 |application/json |PASS |3 |ConfigController#getConfig |
|POST |/api/config |200 |application/json |PASS |5 |ConfigController#updateConfig |
|POST |/api/data/confirm-login |200 |application/json |PASS |4 |DataManageController#confirmLogin |
|POST |/api/data/logs/clear |200 |application/json |PASS |1 |DataManageController#clearLogs |
|GET |/api/data/overview |200 |application/json |PASS |1 |DataManageController#getOverview |
|POST |/api/data/stop |200 |application/json |PASS |2 |DataManageController#stopUpdate |
|POST |/api/data/update |200 |application/json |PASS |1 |DataManageController#startUpdate |
|GET |/api/jobs/page |200 |application/json |PASS |17 |JobInfoController#pageQuery |
|GET |/api/jobs/stats/company-hot |200 |application/json |PASS |4 |JobInfoController#getCompanyHotStats |
|GET |/api/jobs/stats/company-salary |200 |application/json |PASS |3 |JobInfoController#getCompanySalaryStats |
|GET |/api/jobs/stats/company-size |200 |application/json |PASS |1 |JobInfoController#getCompanySizeStats |
|GET |/api/jobs/stats/overview |200 |application/json |PASS |6 |JobInfoController#getOverview |
|GET |/api/jobs51/page |200 |application/json |PASS |6 |JobInfo51JobController#pageQuery |
|GET |/api/jobs51/stats/overview |200 |application/json |PASS |4 |JobInfo51JobController#getOverview |
|GET |/api/pipeline/artifacts |200 |application/json |PASS |9 |PipelineController#artifacts |
|POST |/api/pipeline/dashboard/run |200 |application/json |PASS |4 |PipelineController#runDashboard |
|GET |/api/pipeline/file |200 |application/json |PASS |10 |PipelineController#file |
|POST |/api/pipeline/stats/run |200 |application/json |PASS |1 |PipelineController#runStats |
|GET |/api/pipeline/status |200 |application/json |PASS |1 |PipelineController#status |
|POST |/api/rag/reindex/jobs |200 |application/json |PASS |4 |RagAdminController#reindexJobs |
|POST |/api/rag/reindex/jobs/async |200 |application/json |PASS |3 |RagAdminController#reindexJobsAsync |
|GET |/api/rag/reindex/jobs/status |200 |application/json |PASS |2 |RagAdminController#reindexJobsStatus |
|POST |/api/resume/parse |200 |application/json |PASS |3 |ResumeController#parseResume |
|DELETE |/api/user/favorites |200 |application/json |PASS |3 |UserController#removeFavorite |
|GET |/api/user/favorites |200 |application/json |PASS |2 |UserController#listFavorites |
|POST |/api/user/favorites |200 |application/json |PASS |27 |UserController#addFavorite |
|GET |/api/user/job-history |200 |application/json |PASS |11 |UserController#listJobHistory |
|POST |/api/user/job-history |200 |application/json |PASS |10 |UserController#recordJobHistory |
|GET |/api/user/profile |200 |application/json |PASS |3 |UserController#getProfile |
|PUT |/api/user/profile |200 |application/json |PASS |5 |UserController#upsertProfile |

## 失败详情

