package com.jobdata.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo51Job;

import java.util.List;

/**
 * 51Job 职位数据服务：提供职位查询与统计分析相关能力。
 */
public interface JobInfo51JobService extends IService<JobInfo51Job> {

    /**
     * 分页查询 51Job 职位信息，支持关键词与筛选条件。
     */
    Page<JobInfo51Job> pageQuery(Integer current, Integer size, String keyword, String city, String education, String experience);

    /**
     * 按城市统计职位数量与平均薪资。
     */
    List<CitySalaryDTO> getCitySalaryStats(String keyword, String city, String education, String experience);

    /**
     * 按学历统计职位数量与平均薪资。
     */
    List<EducationSalaryDTO> getEducationSalaryStats(String keyword, String city, String education, String experience);

    /**
     * 按经验统计职位数量与平均薪资。
     */
    List<ExperienceSalaryDTO> getExperienceSalaryStats(String keyword, String city, String education, String experience);

    /**
     * 统计职位关键词热度（出现次数）。
     */
    List<KeywordDTO> getKeywordStats(String keyword, String city, String education, String experience);

    /**
     * 按行业统计职位数量。
     */
    List<IndustryCountDTO> getIndustryStats(String keyword, String city, String education, String experience);

    /**
     * 获取符合条件的职位总量。
     */
    Long getTotalCount(String keyword, String city, String education, String experience);
}
