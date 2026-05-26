package com.jobdata.ai.rag;

import com.jobdata.dto.Result;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagAdminController {

    private final JobRagIndexer jobRagIndexer;

    public RagAdminController(JobRagIndexer jobRagIndexer) {
        this.jobRagIndexer = jobRagIndexer;
    }

    @PostMapping("/reindex/jobs")
    public Result<Map<String, Object>> reindexJobs(
            Authentication authentication,
            @RequestParam(defaultValue = "all") String source,
            @RequestParam(defaultValue = "0") Integer limit,
            @RequestParam(defaultValue = "true") Boolean reset
    ) {
        Map<String, Object> out = jobRagIndexer.reindexJobs(source, limit, Boolean.TRUE.equals(reset));
        return Result.success(out);
    }
}

