package com.jobdata.ai.rag;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RagReindexJobManager {

    private final JobRagIndexer jobRagIndexer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, JobState> jobs = new ConcurrentHashMap<>();
    private volatile String runningJobId = null;

    public RagReindexJobManager(JobRagIndexer jobRagIndexer) {
        this.jobRagIndexer = jobRagIndexer;
    }

    public Map<String, Object> start(String source, Integer limit, Boolean reset) {
        String current = runningJobId;
        if (current != null) {
            JobState st = jobs.get(current);
            if (st != null && "running".equals(st.status)) {
                return st.toMap();
            }
        }

        String jobId = UUID.randomUUID().toString();
        JobState st = new JobState(jobId);
        st.status = "running";
        st.startedAt = LocalDateTime.now();
        st.source = source == null ? "all" : source;
        st.limit = limit == null ? 0 : limit;
        st.reset = reset == null || reset;
        jobs.put(jobId, st);
        runningJobId = jobId;

        executor.submit(() -> {
            try {
                Map<String, Object> result = jobRagIndexer.reindexJobs(
                        st.source,
                        st.limit,
                        st.reset,
                        st.cancel,
                        (p, t) -> {
                            st.processed = p;
                            st.total = t;
                        }
                );
                st.result = result;
                st.endedAt = LocalDateTime.now();
                st.status = st.cancel.get() ? "cancelled" : "done";
            } catch (Exception e) {
                st.endedAt = LocalDateTime.now();
                st.status = "failed";
                st.message = e.getMessage();
            } finally {
                if (jobId.equals(runningJobId)) {
                    runningJobId = null;
                }
            }
        });

        return st.toMap();
    }

    public Map<String, Object> status(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            String current = runningJobId;
            if (current == null) return Map.of("status", "idle");
            JobState st = jobs.get(current);
            return st == null ? Map.of("status", "idle") : st.toMap();
        }
        JobState st = jobs.get(jobId);
        return st == null ? Map.of("status", "not_found") : st.toMap();
    }

    public Map<String, Object> cancel(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            jobId = runningJobId;
        }
        if (jobId == null) return Map.of("success", false, "message", "当前没有运行中的任务");
        JobState st = jobs.get(jobId);
        if (st == null) return Map.of("success", false, "message", "任务不存在");
        st.cancel.set(true);
        return Map.of("success", true, "jobId", jobId);
    }

    private static class JobState {
        private final String jobId;
        private final AtomicBoolean cancel = new AtomicBoolean(false);
        private volatile String status;
        private volatile String message;
        private volatile String source;
        private volatile Integer limit;
        private volatile Boolean reset;
        private volatile Integer processed = 0;
        private volatile Integer total = 0;
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime endedAt;
        private volatile Map<String, Object> result;

        private JobState(String jobId) {
            this.jobId = jobId;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new HashMap<>();
            out.put("jobId", jobId);
            out.put("status", status);
            out.put("message", message == null ? "" : message);
            out.put("source", source == null ? "" : source);
            out.put("limit", limit == null ? 0 : limit);
            out.put("reset", reset != null && reset);
            out.put("processed", processed == null ? 0 : processed);
            out.put("total", total == null ? 0 : total);
            out.put("startedAt", startedAt == null ? "" : startedAt.toString());
            out.put("endedAt", endedAt == null ? "" : endedAt.toString());
            out.put("result", result == null ? Map.of() : result);
            return out;
        }
    }
}
