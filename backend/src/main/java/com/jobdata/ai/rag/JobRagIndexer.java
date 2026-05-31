package com.jobdata.ai.rag;

import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 岗位 RAG 索引器，负责将岗位数据构建为向量文档并存入向量数据库
 */
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

    /**
     * 重新索引岗位数据到向量数据库
     *
     * @param source 数据来源：boss|51job|all
     * @param limit 限制数量，0表示全部
     * @param resetVectorStore 是否清空向量数据库
     * @return 索引结果统计
     */
    public Map<String, Object> reindexJobs(String source, Integer limit, boolean resetVectorStore) {
        return reindexJobs(source, limit, resetVectorStore, null, null);
    }

    public Map<String, Object> reindexJobs(
            String source,
            Integer limit,
            boolean resetVectorStore,
            AtomicBoolean cancel,
            BiConsumer<Integer, Integer> progress
    ) {
        String src = source == null ? "all" : source.trim().toLowerCase();
        int lim = limit == null ? 0 : Math.max(0, limit);

        if (resetVectorStore) {
            try {
                jdbcTemplate.update("DELETE FROM vector_store");
            } catch (Exception ignored) {
            }
        }

        int total = estimateTotal(src, lim);
        int processed = 0;
        if (progress != null) progress.accept(processed, total);

        int batchSize = 10;
        List<Document> batch = new ArrayList<>(batchSize);

        if ("boss".equals(src) || "all".equals(src)) {
            processed = indexBoss(lim, cancel, progress, total, processed, batch, batchSize);
        }

        if ("51job".equals(src) || "all".equals(src)) {
            processed = index51Job(lim, cancel, progress, total, processed, batch, batchSize);
        }

        if (!batch.isEmpty() && (cancel == null || !cancel.get())) {
            processed += safeAdd(batch);
            if (progress != null) progress.accept(processed, total);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("source", src);
        out.put("limit", lim);
        out.put("reset", resetVectorStore);
        out.put("documents", processed);
        return out;
    }

    private int estimateTotal(String src, int lim) {
        long boss = 0;
        long job51 = 0;
        if ("boss".equals(src) || "all".equals(src)) {
            boss = jobInfoService.count();
        }
        if ("51job".equals(src) || "all".equals(src)) {
            job51 = jobInfo51JobService.count();
        }
        long total = boss + job51;
        if (lim > 0) {
            long t = 0;
            if ("boss".equals(src) || "all".equals(src)) t += Math.min(lim, boss);
            if ("51job".equals(src) || "all".equals(src)) t += Math.min(lim, job51);
            total = t;
        }
        if (total > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) total;
    }

    private int indexBoss(
            int lim,
            AtomicBoolean cancel,
            BiConsumer<Integer, Integer> progress,
            int total,
            int processed,
            List<Document> batch,
            int batchSize
    ) {
        int pageSize = 200;
        long offset = 0;
        while (true) {
            if (cancel != null && cancel.get()) return processed;
            if (lim > 0 && offset >= lim) return processed;

            int size = lim > 0 ? (int) Math.min(pageSize, (long) lim - offset) : pageSize;
            Page<JobInfo> page = new Page<>(offset / pageSize + 1, size);
            LambdaQueryWrapper<JobInfo> w = new LambdaQueryWrapper<>();
            w.orderByDesc(JobInfo::getCreatedAt);
            Page<JobInfo> data = jobInfoService.page(page, w);
            List<JobInfo> records = data.getRecords();
            if (records == null || records.isEmpty()) return processed;

            for (JobInfo j : records) {
                if (cancel != null && cancel.get()) return processed;
                Document doc = toDocFromBoss(j);
                if (doc == null) continue;
                batch.add(doc);
                if (batch.size() >= batchSize) {
                    processed += safeAdd(batch);
                    if (progress != null) progress.accept(processed, total);
                }
            }
            offset += records.size();
            if (records.size() < size) return processed;
        }
    }

    private int index51Job(
            int lim,
            AtomicBoolean cancel,
            BiConsumer<Integer, Integer> progress,
            int total,
            int processed,
            List<Document> batch,
            int batchSize
    ) {
        int pageSize = 200;
        long offset = 0;
        while (true) {
            if (cancel != null && cancel.get()) return processed;
            if (lim > 0 && offset >= lim) return processed;

            int size = lim > 0 ? (int) Math.min(pageSize, (long) lim - offset) : pageSize;
            Page<JobInfo51Job> page = new Page<>(offset / pageSize + 1, size);
            LambdaQueryWrapper<JobInfo51Job> w = new LambdaQueryWrapper<>();
            w.orderByDesc(JobInfo51Job::getCreatedAt);
            Page<JobInfo51Job> data = jobInfo51JobService.page(page, w);
            List<JobInfo51Job> records = data.getRecords();
            if (records == null || records.isEmpty()) return processed;

            for (JobInfo51Job j : records) {
                if (cancel != null && cancel.get()) return processed;
                Document doc = toDocFrom51(j);
                if (doc == null) continue;
                batch.add(doc);
                if (batch.size() >= batchSize) {
                    processed += safeAdd(batch);
                    if (progress != null) progress.accept(processed, total);
                }
            }
            offset += records.size();
            if (records.size() < size) return processed;
        }
    }

    private int safeAdd(List<Document> docs) {
        if (docs == null || docs.isEmpty()) return 0;
        int max = 10;
        int added = 0;
        int from = 0;
        while (from < docs.size()) {
            int to = Math.min(docs.size(), from + max);
            vectorStore.add(docs.subList(from, to));
            added += (to - from);
            from = to;
        }
        docs.clear();
        return added;
    }


    /**
     * 将 BOSS 直聘岗位转换为向量文档
     */
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
        put(meta, "salary_min", j.getSalaryMin());
        put(meta, "salary_max", j.getSalaryMax());
        putStr(meta, "job_keywords", j.getJobKeywords());
        putStr(meta, "job_desc", j.getJobDesc());
        putStr(meta, "company_industry", j.getCompanyIndustry());
        putStr(meta, "company_size", j.getCompanySize());
        putStr(meta, "company_welfare", j.getCompanyWelfare());
        put(meta, "publish_date", j.getPublishDate());
        meta.put("title", title(j.getJobName(), j.getCompanyName(), j.getCity()));
        String text = buildJobText(j.getJobName(), j.getCompanyName(), j.getCity(), j.getEducation(), j.getExperience(), j.getJobKeywords(), j.getJobDesc());
        if (!StringUtils.hasText(text)) return null;
        return new Document(text, meta);
    }

    /**
     * 将前程无忧岗位转换为向量文档
     */
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
        put(meta, "salary_min", j.getSalaryMin());
        put(meta, "salary_max", j.getSalaryMax());
        putStr(meta, "job_keywords", j.getJobKeywords());
        putStr(meta, "job_desc", j.getJobDesc());
        putStr(meta, "company_industry", j.getCompanyIndustry());
        putStr(meta, "company_size", j.getCompanySize());
        putStr(meta, "company_welfare", j.getCompanyWelfare());
        put(meta, "publish_date", j.getPublishDate());
        meta.put("title", title(j.getJobName(), j.getCompanyName(), j.getCity()));
        String text = buildJobText(j.getJobName(), j.getCompanyName(), j.getCity(), j.getEducation(), j.getExperience(), j.getJobKeywords(), j.getJobDesc());
        if (!StringUtils.hasText(text)) return null;
        return new Document(text, meta);
    }

    /**
     * 向元数据中添加非空值
     */
    private void put(Map<String, Object> meta, String key, Object value) {
        if (value != null) {
            meta.put(key, value);
        }
    }

    /**
     * 向元数据中添加字符串值（允许空值）
     */
    private void putStr(Map<String, Object> meta, String key, String value) {
        meta.put(key, value == null ? "" : value);
    }

    /**
     * 生成文档标题
     */
    private String title(String jobName, String companyName, String city) {
        String j = jobName == null ? "" : jobName.trim();
        String c = companyName == null ? "" : companyName.trim();
        String ct = city == null ? "" : city.trim();
        String base = (j.isEmpty() ? "岗位" : j) + (c.isEmpty() ? "" : (" - " + c));
        return ct.isEmpty() ? base : (base + "（" + ct + "）");
    }

    /**
     * 构建岗位文本内容，用于向量检索
     */
    private String buildJobText(String jobName, String companyName, String city,
                                String education, String experience,
                                String keywords, String desc) {
        StringBuilder sb = new StringBuilder();

        // 岗位名称
        if (StringUtils.hasText(jobName)) {
            String text = jobName.trim();
            if (text.length() > 100) text = text.substring(0, 100);
            sb.append(text).append("\n");
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
