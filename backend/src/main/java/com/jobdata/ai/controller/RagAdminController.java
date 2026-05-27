package com.jobdata.ai.controller;

import com.jobdata.ai.rag.JobRagIndexer;
import com.jobdata.ai.rag.JobDataCleanupService;
import com.jobdata.dto.Result;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * RAG 管理控制器，提供岗位向量索引的管理接口
 */
@RestController
@RequestMapping("/api/rag")
public class RagAdminController {

    private final JobRagIndexer jobRagIndexer;
    private final JobDataCleanupService jobDataCleanupService;

    public RagAdminController(JobRagIndexer jobRagIndexer, JobDataCleanupService jobDataCleanupService) {
        this.jobRagIndexer = jobRagIndexer;
        this.jobDataCleanupService = jobDataCleanupService;
    }

    /**
     * 重新构建岗位向量索引。
     *
     * @param authentication 当前认证信息
     * @param source 数据来源：boss|51job|all
     * @param limit 限制条数（0 表示全部）
     * @param reset 是否清空向量库后重建
     * @return 重建结果统计
     */
    @PostMapping("/reindex/jobs")
    public Result<Map<String, Object>> reindexJobs(
            Authentication authentication,
            @RequestParam(defaultValue = "all") String source,
            @RequestParam(defaultValue = "0") Integer limit,
            @RequestParam(defaultValue = "true") Boolean reset
    ) {
        Map<String, Object> dedup = jobDataCleanupService.dedupeJobUrl(source);
        Map<String, Object> out = jobRagIndexer.reindexJobs(source, limit, Boolean.TRUE.equals(reset));
        out.put("dedup", dedup);
        return Result.success(out);
    }
}
