package com.jobdata.ai.rag;

import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JobRagIndexer {

    private final JobInfoService jobInfoService;
    private final JobInfo51JobService jobInfo51JobService;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public JobRagIndexer(JobInfoService jobInfoService, JobInfo51JobService jobInfo51JobService, VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.jobInfoService = jobInfoService;
        this.jobInfo51JobService = jobInfo51JobService;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> reindexJobs(String source, Integer limit, boolean resetVectorStore) {
        String src = source == null ? "all" : source.trim().toLowerCase();
        int lim = limit == null ? 0 : Math.max(0, limit);

        if (resetVectorStore) {
            try {
                jdbcTemplate.update("DELETE FROM vector_store");
            } catch (Exception ignored) {
            }
        }

        List<Document> docs = new ArrayList<>();
        if ("boss".equals(src) || "all".equals(src)) {
            List<JobInfo> list;
            if (lim > 0) {
                LambdaQueryWrapper<JobInfo> w = new LambdaQueryWrapper<>();
                w.orderByDesc(JobInfo::getCreatedAt);
                w.last("LIMIT " + lim);
                list = jobInfoService.list(w);
            } else {
                list = jobInfoService.list();
            }
            for (JobInfo j : list) {
                docs.add(toDocFromBoss(j));
            }
        }
        if ("51job".equals(src) || "all".equals(src)) {
            List<JobInfo51Job> list;
            if (lim > 0) {
                LambdaQueryWrapper<JobInfo51Job> w = new LambdaQueryWrapper<>();
                w.orderByDesc(JobInfo51Job::getCreatedAt);
                w.last("LIMIT " + lim);
                list = jobInfo51JobService.list(w);
            } else {
                list = jobInfo51JobService.list();
            }
            for (JobInfo51Job j : list) {
                docs.add(toDocFrom51(j));
            }
        }

        if (!docs.isEmpty()) {
            int batchSize = 10;
            for (int i = 0; i < docs.size(); i += batchSize) {
                int end = Math.min(i + batchSize, docs.size());
                List<Document> batch = docs.subList(i, end);
                try {
                    vectorStore.add(batch);
                } catch (Exception e) {
                    System.err.println("Failed to add batch starting at index " + i + ": " + e.getMessage());
                    throw e;
                }
            }
        }

        Map<String, Object> out = new HashMap<>();
        out.put("source", src);
        out.put("limit", lim);
        out.put("reset", resetVectorStore);
        out.put("documents", docs.size());
        return out;
    }


    private Document toDocFromBoss(JobInfo j) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "boss");
        meta.put("source_table", "job_info");
        put(meta, "job_id", j.getId());
        putStr(meta, "job_url", j.getJobUrl());
        putStr(meta, "job_name", j.getJobName());
        putStr(meta, "company_name", j.getCompanyName());
        putStr(meta, "city", j.getCity());
        putStr(meta, "education", j.getEducation());
        putStr(meta, "experience", j.getExperience());
        meta.put("title", title(j.getJobName(), j.getCompanyName(), j.getCity()));
        return new Document(buildJobText(j.getJobName(), j.getCompanyName(), j.getCity(), j.getEducation(), j.getExperience(), j.getJobKeywords(), j.getJobDesc()), meta);
    }

    private Document toDocFrom51(JobInfo51Job j) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "51job");
        meta.put("source_table", "job_info_51job");
        put(meta, "job_id", j.getId());
        putStr(meta, "job_url", j.getJobUrl());
        putStr(meta, "job_name", j.getJobName());
        putStr(meta, "company_name", j.getCompanyName());
        putStr(meta, "city", j.getCity());
        putStr(meta, "education", j.getEducation());
        putStr(meta, "experience", j.getExperience());
        meta.put("title", title(j.getJobName(), j.getCompanyName(), j.getCity()));
        return new Document(buildJobText(j.getJobName(), j.getCompanyName(), j.getCity(), j.getEducation(), j.getExperience(), j.getJobKeywords(), j.getJobDesc()), meta);
    }

    private void put(Map<String, Object> meta, String key, Object value) {
        if (value != null) {
            meta.put(key, value);
        }
    }

    private void putStr(Map<String, Object> meta, String key, String value) {
        meta.put(key, value == null ? "" : value);
    }

    private String title(String jobName, String companyName, String city) {
        String j = jobName == null ? "" : jobName.trim();
        String c = companyName == null ? "" : companyName.trim();
        String ct = city == null ? "" : city.trim();
        String base = (j.isEmpty() ? "岗位" : j) + (c.isEmpty() ? "" : (" - " + c));
        return ct.isEmpty() ? base : (base + "（" + ct + "）");
    }

    private String buildJobText(String jobName, String companyName, String city,
                                String education, String experience,
                                String keywords, String desc) {
        StringBuilder sb = new StringBuilder();

        // 岗位名称 - 最多 100 字符
        if (StringUtils.hasText(jobName)) {
            String text = jobName.trim();
            if (text.length() > 100) text = text.substring(0, 100);
            sb.append("岗位: ").append(text).append("\n");
        }

        // 公司名称 - 最多 100 字符
        if (StringUtils.hasText(companyName)) {
            String text = companyName.trim();
            if (text.length() > 100) text = text.substring(0, 100);
            sb.append("公司: ").append(text).append("\n");
        }

        // 城市 - 最多 50 字符
        if (StringUtils.hasText(city)) {
            String text = city.trim();
            if (text.length() > 50) text = text.substring(0, 50);
            sb.append("城市: ").append(text).append("\n");
        }

        // 学历 - 最多 50 字符
        if (StringUtils.hasText(education)) {
            String text = education.trim();
            if (text.length() > 50) text = text.substring(0, 50);
            sb.append("学历: ").append(text).append("\n");
        }

        // 经验 - 最多 100 字符
        if (StringUtils.hasText(experience)) {
            String text = experience.trim();
            if (text.length() > 100) text = text.substring(0, 100);
            sb.append("经验: ").append(text).append("\n");
        }

        // 关键词 - 最多 200 字符
        if (StringUtils.hasText(keywords)) {
            String text = keywords.trim();
            if (text.length() > 200) text = text.substring(0, 200);
            sb.append("关键词: ").append(text).append("\n");
        }

        // 职位描述 - 最多 5000 字符（约 7500 tokens，留有余量）
        if (StringUtils.hasText(desc)) {
            String text = desc.trim();
            if (text.length() > 5000) text = text.substring(0, 5000);
            sb.append("描述: ").append(text).append("\n");
        }

        return sb.toString().trim();
    }

}
