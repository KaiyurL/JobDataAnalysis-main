package com.jobdata.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 51Job 职位数据接口：提供职位分页查询与统计分析数据。
 */
@RestController
@RequestMapping("/api/jobs51")
@CrossOrigin(origins = "*")
public class JobInfo51JobController {

    @Autowired
    private JobInfo51JobService jobInfo51JobService;

    /**
     * 分页查询 51Job 职位信息，支持关键词与条件筛选。
     *
     * @param current 当前页
     * @param size 每页数量
     * @param keyword 关键词（可选）
     * @param city 城市（可选）
     * @param education 学历（可选）
     * @param experience 经验（可选）
     * @return 分页结果
     */
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

    /**
     * 获取 51Job 职位统计总览数据（总量、城市薪资、学历薪资、经验薪资、关键词、行业等）。
     *
     * @param keyword 关键词（可选）
     * @param city 城市（可选）
     * @param education 学历（可选）
     * @param experience 经验（可选）
     * @return 总览统计数据
     */
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
