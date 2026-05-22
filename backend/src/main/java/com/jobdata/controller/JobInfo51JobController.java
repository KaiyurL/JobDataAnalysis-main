package com.jobdata.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs51")
@CrossOrigin(origins = "*")
public class JobInfo51JobController {

    @Autowired
    private JobInfo51JobService jobInfo51JobService;

    @GetMapping("/page")
    public Result<Page<JobInfo51Job>> pageQuery(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        Page<JobInfo51Job> page = jobInfo51JobService.pageQuery(current, size, keyword, city, education, experience);
        return Result.success(page);
    }

    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> getOverview(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String experience) {
        Map<String, Object> overview = new HashMap<>();
        overview.put("total", jobInfo51JobService.getTotalCount(keyword, city, education, experience));
        overview.put("citySalary", jobInfo51JobService.getCitySalaryStats(keyword, city, education, experience));
        overview.put("educationSalary", jobInfo51JobService.getEducationSalaryStats(keyword, city, education, experience));
        overview.put("experienceSalary", jobInfo51JobService.getExperienceSalaryStats(keyword, city, education, experience));
        overview.put("keywords", jobInfo51JobService.getKeywordStats(keyword, city, education, experience));
        overview.put("industry", jobInfo51JobService.getIndustryStats(keyword, city, education, experience));
        return Result.success(overview);
    }
}
