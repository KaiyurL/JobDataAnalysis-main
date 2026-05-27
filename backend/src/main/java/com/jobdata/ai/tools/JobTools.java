package com.jobdata.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位检索工具类，提供大模型可调用的 job_search 工具
 */
@Component
public class JobTools {

    private final JobInfoService jobInfoService;
    private final JobInfo51JobService jobInfo51JobService;
    private final JobToolResultStore jobToolResultStore;

    public JobTools(JobInfoService jobInfoService, JobInfo51JobService jobInfo51JobService, JobToolResultStore jobToolResultStore) {
        this.jobInfoService = jobInfoService;
        this.jobInfo51JobService = jobInfo51JobService;
        this.jobToolResultStore = jobToolResultStore;
    }


    /**
     * 从数据库检索招聘岗位，支持多条件筛选
     *
     * @param source 数据来源：51job|boss|all
     * @param keyword 关键词：岗位/公司关键字
     * @param city 城市，支持逗号分隔多个城市
     * @param education 学历要求
     * @param experience 经验要求
     * @param minSalaryK 最低月薪(K)
     * @param maxSalaryK 最高月薪(K)
     * @param company 公司名关键词
     * @param limit 返回数量上限，建议<=10
     * @return 岗位列表
     */
    @Tool(name = "job_search", description = "按条件从数据库检索招聘岗位，返回岗位列表（真实数据）。当用户提出要推荐更多岗位或按薪资/城市/学历/经验/公司等筛选时使用。")
    public List<Map<String, Object>> jobSearch(
            @ToolParam(description = "数据来源：51job|boss|all", required = false) String source,
            @ToolParam(description = "关键词：岗位/公司关键字，可为空", required = false) String keyword,
            @ToolParam(description = "城市，支持逗号分隔多个城市，可为空", required = false) String city,
            @ToolParam(description = "学历要求，可为空", required = false) String education,
            @ToolParam(description = "经验要求，可为空", required = false) String experience,
            @ToolParam(description = "最低月薪(K)，如5000元约等于5，可为空", required = false) Integer minSalaryK,
            @ToolParam(description = "最高月薪(K)，可为空", required = false) Integer maxSalaryK,
            @ToolParam(description = "公司名关键词，可为空", required = false) String company,
            @ToolParam(description = "返回数量上限，建议<=10", required = false) Integer limit
    ) {
        int lim = limit == null ? 10 : Math.max(1, Math.min(50, limit));
        String src = source == null ? "all" : source.trim().toLowerCase();

        List<Map<String, Object>> out = new ArrayList<>();
        if ("boss".equals(src) || "all".equals(src)) {
            out.addAll(searchBoss(keyword, city, education, experience, minSalaryK, maxSalaryK, company, lim));
        }
        if ("51job".equals(src) || "all".equals(src)) {
            out.addAll(search51(keyword, city, education, experience, minSalaryK, maxSalaryK, company, lim));
        }

        if (out.size() > lim) {
            List<Map<String, Object>> sliced = out.subList(0, lim);
            jobToolResultStore.setLastJobCards(sliced);
            return sliced;
        }
        jobToolResultStore.setLastJobCards(out);
        return out;
    }



    /**
     * 检索 BOSS 直聘岗位
     */
    private List<Map<String, Object>> searchBoss(
            String keyword,
            String city,
            String education,
            String experience,
            Integer minSalaryK,
            Integer maxSalaryK,
            String company,
            int limit
    ) {
        LambdaQueryWrapper<JobInfo> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(x -> x.like(JobInfo::getJobName, kw).or().like(JobInfo::getCompanyName, kw));
        }
        if (StringUtils.hasText(company)) {
            w.like(JobInfo::getCompanyName, company.trim());
        }
        if (StringUtils.hasText(city)) {
            w.in(JobInfo::getCity, Arrays.asList(city.split(",")));
        }
        if (StringUtils.hasText(education)) {
            w.eq(JobInfo::getEducation, education.trim());
        }
        if (StringUtils.hasText(experience)) {
            w.eq(JobInfo::getExperience, experience.trim());
        }
        if (minSalaryK != null) {
            w.ge(JobInfo::getSalaryMax, minSalaryK);
        }
        if (maxSalaryK != null) {
            w.le(JobInfo::getSalaryMin, maxSalaryK);
        }
        w.orderByDesc(JobInfo::getCreatedAt);
        w.last("LIMIT " + Math.max(1, Math.min(200, limit)));

        List<JobInfo> list = jobInfoService.list(w);
        List<Map<String, Object>> out = new ArrayList<>();
        for (JobInfo j : list) {
            out.add(toCard(j, "boss"));
        }
        return out;
    }

    /**
     * 检索前程无忧岗位
     */
    private List<Map<String, Object>> search51(
            String keyword,
            String city,
            String education,
            String experience,
            Integer minSalaryK,
            Integer maxSalaryK,
            String company,
            int limit
    ) {
        LambdaQueryWrapper<JobInfo51Job> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(x -> x.like(JobInfo51Job::getJobName, kw).or().like(JobInfo51Job::getCompanyName, kw));
        }
        if (StringUtils.hasText(company)) {
            w.like(JobInfo51Job::getCompanyName, company.trim());
        }
        if (StringUtils.hasText(city)) {
            w.in(JobInfo51Job::getCity, Arrays.asList(city.split(",")));
        }
        if (StringUtils.hasText(education)) {
            w.eq(JobInfo51Job::getEducation, education.trim());
        }
        if (StringUtils.hasText(experience)) {
            w.eq(JobInfo51Job::getExperience, experience.trim());
        }
        if (minSalaryK != null) {
            w.ge(JobInfo51Job::getSalaryMax, minSalaryK);
        }
        if (maxSalaryK != null) {
            w.le(JobInfo51Job::getSalaryMin, maxSalaryK);
        }
        w.orderByDesc(JobInfo51Job::getCreatedAt);
        w.last("LIMIT " + Math.max(1, Math.min(200, limit)));

        List<JobInfo51Job> list = jobInfo51JobService.list(w);
        List<Map<String, Object>> out = new ArrayList<>();
        for (JobInfo51Job j : list) {
            out.add(toCard(j, "51job"));
        }
        return out;
    }

    /**
     * 将 BOSS 直聘岗位转换为卡片格式
     */
    private Map<String, Object> toCard(JobInfo j, String source) {
        Map<String, Object> m = new HashMap<>();
        m.put("source", source);
        m.put("id", j.getId());
        m.put("jobName", j.getJobName());
        m.put("companyName", j.getCompanyName());
        m.put("city", j.getCity());
        m.put("jobUrl", j.getJobUrl());
        m.put("salaryMin", j.getSalaryMin());
        m.put("salaryMax", j.getSalaryMax());
        m.put("salaryAvg", j.getSalaryAvg());
        m.put("experience", j.getExperience());
        m.put("education", j.getEducation());
        m.put("jobDesc", j.getJobDesc());
        m.put("jobKeywords", j.getJobKeywords());
        m.put("companySize", j.getCompanySize());
        m.put("companyIndustry", j.getCompanyIndustry());
        m.put("companyWelfare", j.getCompanyWelfare());
        m.put("publishDate", j.getPublishDate());
        m.put("createdAt", j.getCreatedAt());
        return m;
    }

    /**
     * 将前程无忧岗位转换为卡片格式
     */
    private Map<String, Object> toCard(JobInfo51Job j, String source) {
        Map<String, Object> m = new HashMap<>();
        m.put("source", source);
        m.put("id", j.getId());
        m.put("jobName", j.getJobName());
        m.put("companyName", j.getCompanyName());
        m.put("city", j.getCity());
        m.put("jobUrl", j.getJobUrl());
        m.put("salaryMin", j.getSalaryMin());
        m.put("salaryMax", j.getSalaryMax());
        m.put("salaryAvg", j.getSalaryAvg());
        m.put("experience", j.getExperience());
        m.put("education", j.getEducation());
        m.put("jobDesc", j.getJobDesc());
        m.put("jobKeywords", j.getJobKeywords());
        m.put("companySize", j.getCompanySize());
        m.put("companyIndustry", j.getCompanyIndustry());
        m.put("companyWelfare", j.getCompanyWelfare());
        m.put("publishDate", j.getPublishDate());
        m.put("createdAt", j.getCreatedAt());
        return m;
    }
}
