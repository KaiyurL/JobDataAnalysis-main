# API 自动化测试报告

- 总用例数: 33
- 通过: 33
- 失败: 0
- 生成时间: 2026-05-28T01:16:28.214562900

| Method | Path | Status | Content-Type | OK | Latency(ms) | Handler |
|---|---|---:|---|---|---:|---|
|POST |/api/agent/chat |200 |application/json |PASS |20 |AgentController#chat |
|POST |/api/agent/chat/stream |200 | |PASS |1 |AgentController#chatStream |
|POST |/api/auth/login |200 |application/json |PASS |6 |AuthController#login |
|POST |/api/auth/register |200 |application/json |PASS |1 |AuthController#register |
|GET |/api/config |200 |application/json |PASS |1 |ConfigController#getConfig |
|POST |/api/config |200 |application/json |PASS |1 |ConfigController#updateConfig |
|POST |/api/data/confirm-login |200 |application/json |PASS |0 |DataManageController#confirmLogin |
|POST |/api/data/logs/clear |200 |application/json |PASS |1 |DataManageController#clearLogs |
|GET |/api/data/overview |200 |application/json |PASS |0 |DataManageController#getOverview |
|POST |/api/data/update |200 |application/json |PASS |0 |DataManageController#startUpdate |
|GET |/api/jobs/page |200 |application/json |PASS |6 |JobInfoController#pageQuery |
|GET |/api/jobs/stats/company-hot |200 |application/json |PASS |1 |JobInfoController#getCompanyHotStats |
|GET |/api/jobs/stats/company-salary |200 |application/json |PASS |1 |JobInfoController#getCompanySalaryStats |
|GET |/api/jobs/stats/company-size |200 |application/json |PASS |0 |JobInfoController#getCompanySizeStats |
|GET |/api/jobs/stats/overview |200 |application/json |PASS |6 |JobInfoController#getOverview |
|GET |/api/jobs51/page |200 |application/json |PASS |1 |JobInfo51JobController#pageQuery |
|GET |/api/jobs51/stats/overview |200 |application/json |PASS |4 |JobInfo51JobController#getOverview |
|GET |/api/pipeline/artifacts |200 |application/json |PASS |5 |PipelineController#artifacts |
|POST |/api/pipeline/dashboard/run |200 |application/json |PASS |3 |PipelineController#runDashboard |
|GET |/api/pipeline/file |200 |application/json |PASS |6 |PipelineController#file |
|POST |/api/pipeline/stats/run |200 |application/json |PASS |1 |PipelineController#runStats |
|GET |/api/pipeline/status |200 |application/json |PASS |0 |PipelineController#status |
|POST |/api/rag/reindex/jobs |200 |application/json |PASS |2 |RagAdminController#reindexJobs |
|POST |/api/resume/parse |200 |application/json |PASS |2 |ResumeController#parseResume |
|DELETE |/api/user/favorites |200 |application/json |PASS |2 |UserController#removeFavorite |
|GET |/api/user/favorites |200 |application/json |PASS |1 |UserController#listFavorites |
|POST |/api/user/favorites |200 |application/json |PASS |9 |UserController#addFavorite |
|GET |/api/user/job-history |200 |application/json |PASS |8 |UserController#listJobHistory |
|POST |/api/user/job-history |200 |application/json |PASS |8 |UserController#recordJobHistory |
|GET |/api/user/match-history |200 |application/json |PASS |6 |UserController#listMatchHistory |
|GET |/api/user/match-history/1 |200 |application/json |PASS |2 |UserController#getMatchHistoryDetail |
|GET |/api/user/profile |200 |application/json |PASS |1 |UserController#getProfile |
|PUT |/api/user/profile |200 |application/json |PASS |1 |UserController#upsertProfile |

## 失败详情

