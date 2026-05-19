package com.jobdata.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo;
import com.jobdata.service.JobInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobInfoController {

    @Autowired
    private JobInfoService jobInfoService;

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
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getCitySalaryStats(keyword, education, experience));
    }

    @GetMapping("/stats/education-salary")
    public Result<List<EducationSalaryDTO>> getEducationSalaryStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getEducationSalaryStats(keyword, education, experience));
    }

    @GetMapping("/stats/experience-salary")
    public Result<List<ExperienceSalaryDTO>> getExperienceSalaryStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        return Result.success(jobInfoService.getExperienceSalaryStats(keyword, education, experience));
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
        overview.put("citySalary", jobInfoService.getCitySalaryStats(keyword, education, experience));
        overview.put("educationSalary", jobInfoService.getEducationSalaryStats(keyword, education, experience));
        overview.put("experienceSalary", jobInfoService.getExperienceSalaryStats(keyword, education, experience));
        overview.put("keywords", jobInfoService.getKeywordStats(keyword, city, education, experience));
        overview.put("industry", jobInfoService.getIndustryStats(keyword, city, education, experience));
        return Result.success(overview);
    }

    @PostMapping("/predict/salary")
    public Result<SalaryPredictResponse> predictSalary(@RequestBody SalaryPredictRequest request) {
        return Result.success(jobInfoService.predictSalary(request));
    }

    @PostMapping("/match/jobs")
    public Result<List<JobMatchResponse>> matchJobs(@RequestBody JobMatchRequest request) {
        return Result.success(jobInfoService.matchJobs(request));
    }

    @GetMapping("/skills")
    public Result<List<String>> getAllSkills() {
        return Result.success(jobInfoService.getAllSkills());
    }

    @GetMapping("/skills/all")
    public Result<List<String>> getAllSkillsSorted() {
        return Result.success(jobInfoService.getAllSkills());
    }

    @GetMapping("/stats/company-hot")
    public Result<List<CompanyHotDTO>> getCompanyHotStats() {
        return Result.success(jobInfoService.getCompanyHotStats());
    }

    @GetMapping("/stats/company-salary")
    public Result<List<CompanySalaryDTO>> getCompanySalaryStats() {
        return Result.success(jobInfoService.getCompanySalaryStats());
    }

    @GetMapping("/stats/company-size")
    public Result<List<CompanySizeDTO>> getCompanySizeStats() {
        return Result.success(jobInfoService.getCompanySizeStats());
    }

}
