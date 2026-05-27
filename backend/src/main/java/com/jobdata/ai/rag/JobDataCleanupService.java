package com.jobdata.ai.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class JobDataCleanupService {
    private final JdbcTemplate jdbcTemplate;

    public JobDataCleanupService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> dedupeJobUrl(String source) {
        String src = source == null ? "all" : source.trim().toLowerCase();

        int bossDeleted = 0;
        int job51Deleted = 0;
        if ("boss".equals(src) || "all".equals(src)) {
            bossDeleted = dedupeTable("job_info");
        }
        if ("51job".equals(src) || "all".equals(src)) {
            job51Deleted = dedupeTable("job_info_51job");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("source", src);
        out.put("bossDeleted", bossDeleted);
        out.put("job51Deleted", job51Deleted);
        out.put("totalDeleted", bossDeleted + job51Deleted);
        return out;
    }

    private int dedupeTable(String tableName) {
        String sql = """
                WITH ranked AS (
                    SELECT ctid,
                           row_number() OVER (PARTITION BY job_url ORDER BY created_at DESC NULLS LAST, id DESC) AS rn
                    FROM %s
                    WHERE job_url IS NOT NULL AND job_url <> ''
                )
                DELETE FROM %s t
                USING ranked r
                WHERE t.ctid = r.ctid AND r.rn > 1
                """.formatted(tableName, tableName);

        return jdbcTemplate.update(sql);
    }
}
