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

/**
 * 职位数据接口：提供职位分页查询与统计分析数据。
 */
@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobInfoController {

    @Autowired
    private JobInfoService jobInfoService;

    /**
     * 分页查询职位信息，支持关键词与条件筛选。
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

    /**
     * 获取职位统计总览数据（总量、城市薪资、学历薪资、经验薪资、关键词、行业等）。
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
        overview.put("total", jobInfoService.getTotalCount(keyword, city, education, experience));
        overview.put("citySalary", jobInfoService.getCitySalaryStats(keyword, city, education, experience));
        overview.put("educationSalary", jobInfoService.getEducationSalaryStats(keyword, city, education, experience));
        overview.put("experienceSalary", jobInfoService.getExperienceSalaryStats(keyword, city, education, experience));
        overview.put("keywords", jobInfoService.getKeywordStats(keyword, city, education, experience));
        overview.put("industry", jobInfoService.getIndustryStats(keyword, city, education, experience));
        return Result.success(overview);
    }

    /**
     * 获取热门公司统计数据。
     *
     * @return 热门公司列表
     */
    @GetMapping("/stats/company-hot")
    public Result<List<CompanyHotDTO>> getCompanyHotStats() {
        return Result.success(jobInfoService.getCompanyHotStats());
    }

    /**
     * 获取公司薪资统计数据。
     *
     * @return 公司薪资列表
     */
    @GetMapping("/stats/company-salary")
    public Result<List<CompanySalaryDTO>> getCompanySalaryStats() {
        return Result.success(jobInfoService.getCompanySalaryStats());
    }

    /**
     * 获取公司规模统计数据。
     *
     * @return 公司规模列表
     */
    @GetMapping("/stats/company-size")
    public Result<List<CompanySizeDTO>> getCompanySizeStats() {
        return Result.success(jobInfoService.getCompanySizeStats());
    }

}
