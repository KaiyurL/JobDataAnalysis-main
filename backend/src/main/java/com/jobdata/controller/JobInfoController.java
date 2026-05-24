package com.jobdata.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.entity.UserMatchHistory;
import com.jobdata.service.JobInfoService;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.UserMatchHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobInfoController {

    @Autowired
    private JobInfoService jobInfoService;
    @Autowired
    private JobInfo51JobService jobInfo51JobService;
    @Autowired
    private UserMatchHistoryService userMatchHistoryService;
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/page")
    public Result<Page<JobInfo>> pageQuery(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        Page<JobInfo> page = jobInfoService.pageQuery(current, size, keyword, city, education, experience);
        return Result.success(page);
    }

    @GetMapping("/stats/city-salary")
    public Result<List<CitySalaryDTO>> getCitySalaryStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getCitySalaryStats(keyword, city, education, experience));
    }

    @GetMapping("/stats/education-salary")
    public Result<List<EducationSalaryDTO>> getEducationSalaryStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getEducationSalaryStats(keyword, city, education, experience));
    }

    @GetMapping("/stats/experience-salary")
    public Result<List<ExperienceSalaryDTO>> getExperienceSalaryStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getExperienceSalaryStats(keyword, city, education, experience));
    }

    @GetMapping("/stats/keywords")
    public Result<List<KeywordDTO>> getKeywordStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getKeywordStats(keyword, city, education, experience));
    }

    @GetMapping("/stats/industry")
    public Result<List<IndustryCountDTO>> getIndustryStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getIndustryStats(keyword, city, education, experience));
    }

    @GetMapping("/stats/total")
    public Result<Long> getTotalCount(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getTotalCount(keyword, city, education, experience));
    }

    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        Map<String, Object> overview = new HashMap<>();
        overview.put("total", jobInfoService.getTotalCount(keyword, city, education, experience));
        overview.put("citySalary", jobInfoService.getCitySalaryStats(keyword, city, education, experience));
        overview.put("educationSalary", jobInfoService.getEducationSalaryStats(keyword, city, education, experience));
        overview.put("experienceSalary", jobInfoService.getExperienceSalaryStats(keyword, city, education, experience));
        overview.put("keywords", jobInfoService.getKeywordStats(keyword, city, education, experience));
        overview.put("industry", jobInfoService.getIndustryStats(keyword, city, education, experience));
        return Result.success(overview);
    }

    @PostMapping("/predict/salary")
    public Result<SalaryPredictResponse> predictSalary(@RequestBody SalaryPredictRequest request) {
        return Result.success(jobInfoService.predictSalary(request));
    }

    @GetMapping("/stats/company-hot")
    public Result<List<CompanyHotDTO>> getCompanyHotStats() {
        return Result.success(jobInfoService.getCompanyHotStats());
    }

    @GetMapping("/stats/company-salary")
    public Result<List<CompanySalaryDTO>> getCompanySalaryStats() {
        return Result.success(jobInfoService.getCompanySalaryStats());
    }

    @PostMapping("/match/jobs")
    public Result<List<JobMatchDTO>> matchJobs(Authentication authentication, @RequestBody JobMatchRequest request) {
        List<JobMatchDTO> list = jobInfoService.matchJobs(request);

        if (authentication != null && authentication.getPrincipal() instanceof Long) {
            Long userId = (Long) authentication.getPrincipal();
            UserMatchHistory row = new UserMatchHistory();
            row.setUserId(userId);
            row.setTargetRole(request != null ? request.getTargetRole() : null);
            row.setCity(request != null ? request.getCity() : null);
            row.setCreatedAt(LocalDateTime.now());
            try {
                row.setProfileJson(objectMapper.writeValueAsString(request));
            } catch (Exception e) {
                row.setProfileJson(null);
            }
            try {
                row.setResultJson(objectMapper.writeValueAsString(list));
            } catch (Exception e) {
                row.setResultJson(null);
            }
            userMatchHistoryService.save(row);
        }

        return Result.success(list);
    }

    @PostMapping("/search")
    public Result<List<Map<String, Object>>> search(Authentication authentication, @RequestBody JobSearchRequest request) {
        int limit = request == null || request.getLimit() == null ? 10 : Math.max(1, Math.min(50, request.getLimit()));
        String source = request == null ? "" : String.valueOf(request.getSource() == null ? "" : request.getSource()).trim().toLowerCase();
        if (source.isEmpty()) {
            source = "all";
        }

        List<Map<String, Object>> out = new ArrayList<>();
        if ("51job".equals(source) || "job_info_51job".equals(source) || "all".equals(source)) {
            List<JobInfo51Job> list = search51WithRelax(request, limit);
            for (JobInfo51Job j : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("sourceTable", "job_info_51job");
                row.put("job", objectMapper.convertValue(j, Map.class));
                out.add(row);
            }
        }

        if ("boss".equals(source) || "job_info".equals(source) || "all".equals(source)) {
            List<JobInfo> list = searchBossWithRelax(request, limit);
            for (JobInfo j : list) {
                Map<String, Object> row = new HashMap<>();
                row.put("sourceTable", "job_info");
                row.put("job", objectMapper.convertValue(j, Map.class));
                out.add(row);
            }
        }

        return Result.success(out);
    }

    private List<JobInfo> searchBossWithRelax(JobSearchRequest request, int limit) {
        boolean hasCity = request != null && org.springframework.util.StringUtils.hasText(request.getCity());
        boolean hasEducation = request != null && org.springframework.util.StringUtils.hasText(request.getEducation());
        boolean hasExperience = request != null && org.springframework.util.StringUtils.hasText(request.getExperience());

        for (int relax = 0; relax <= 3; relax++) {
            boolean useEducation = hasEducation && relax < 1;
            boolean useExperience = hasExperience && relax < 2;
            boolean useCity = hasCity && relax < 3;

            LambdaQueryWrapper<JobInfo> w = buildSearchWrapperBoss(request, useCity, useEducation, useExperience);
            w.orderByDesc(JobInfo::getCreatedAt);
            w.last("limit " + limit);
            List<JobInfo> list = jobInfoService.list(w);
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    private List<JobInfo51Job> search51WithRelax(JobSearchRequest request, int limit) {
        boolean hasCity = request != null && org.springframework.util.StringUtils.hasText(request.getCity());
        boolean hasEducation = request != null && org.springframework.util.StringUtils.hasText(request.getEducation());
        boolean hasExperience = request != null && org.springframework.util.StringUtils.hasText(request.getExperience());

        for (int relax = 0; relax <= 3; relax++) {
            boolean useEducation = hasEducation && relax < 1;
            boolean useExperience = hasExperience && relax < 2;
            boolean useCity = hasCity && relax < 3;

            LambdaQueryWrapper<JobInfo51Job> w = buildSearchWrapper51(request, useCity, useEducation, useExperience);
            w.orderByDesc(JobInfo51Job::getCreatedAt);
            w.last("limit " + limit);
            List<JobInfo51Job> list = jobInfo51JobService.list(w);
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        return new ArrayList<>();
    }

    private LambdaQueryWrapper<JobInfo> buildSearchWrapperBoss(JobSearchRequest request, boolean useCity, boolean useEducation, boolean useExperience) {
        LambdaQueryWrapper<JobInfo> w = new LambdaQueryWrapper<>();
        if (request == null) return w;

        if (org.springframework.util.StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().trim();
            w.and(x -> x.like(JobInfo::getJobName, kw).or().like(JobInfo::getCompanyName, kw));
        }
        if (org.springframework.util.StringUtils.hasText(request.getCompany())) {
            w.like(JobInfo::getCompanyName, request.getCompany().trim());
        }
        if (useCity && org.springframework.util.StringUtils.hasText(request.getCity())) {
            w.in(JobInfo::getCity, Arrays.asList(request.getCity().split(",")));
        }
        if (useEducation && org.springframework.util.StringUtils.hasText(request.getEducation())) {
            w.eq(JobInfo::getEducation, request.getEducation().trim());
        }
        if (useExperience && org.springframework.util.StringUtils.hasText(request.getExperience())) {
            w.eq(JobInfo::getExperience, request.getExperience().trim());
        }
        if (request.getMinSalaryK() != null) {
            w.ge(JobInfo::getSalaryMax, request.getMinSalaryK());
        }
        if (request.getMaxSalaryK() != null) {
            w.le(JobInfo::getSalaryMin, request.getMaxSalaryK());
        }
        return w;
    }

    private LambdaQueryWrapper<JobInfo51Job> buildSearchWrapper51(JobSearchRequest request, boolean useCity, boolean useEducation, boolean useExperience) {
        LambdaQueryWrapper<JobInfo51Job> w = new LambdaQueryWrapper<>();
        if (request == null) return w;

        if (org.springframework.util.StringUtils.hasText(request.getKeyword())) {
            String kw = request.getKeyword().trim();
            w.and(x -> x.like(JobInfo51Job::getJobName, kw).or().like(JobInfo51Job::getCompanyName, kw));
        }
        if (org.springframework.util.StringUtils.hasText(request.getCompany())) {
            w.like(JobInfo51Job::getCompanyName, request.getCompany().trim());
        }
        if (useCity && org.springframework.util.StringUtils.hasText(request.getCity())) {
            w.in(JobInfo51Job::getCity, Arrays.asList(request.getCity().split(",")));
        }
        if (useEducation && org.springframework.util.StringUtils.hasText(request.getEducation())) {
            w.eq(JobInfo51Job::getEducation, request.getEducation().trim());
        }
        if (useExperience && org.springframework.util.StringUtils.hasText(request.getExperience())) {
            w.eq(JobInfo51Job::getExperience, request.getExperience().trim());
        }
        if (request.getMinSalaryK() != null) {
            w.ge(JobInfo51Job::getSalaryMax, request.getMinSalaryK());
        }
        if (request.getMaxSalaryK() != null) {
            w.le(JobInfo51Job::getSalaryMin, request.getMaxSalaryK());
        }
        return w;
    }

}
