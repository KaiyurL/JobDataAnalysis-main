package com.jobdata.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo51Job;

import java.util.List;

public interface JobInfo51JobService extends IService<JobInfo51Job> {

    Page<JobInfo51Job> pageQuery(Integer current, Integer size, String keyword, String city, String education, String experience);

    List<CitySalaryDTO> getCitySalaryStats(String keyword, String city, String education, String experience);

    List<EducationSalaryDTO> getEducationSalaryStats(String keyword, String city, String education, String experience);

    List<ExperienceSalaryDTO> getExperienceSalaryStats(String keyword, String city, String education, String experience);

    List<KeywordDTO> getKeywordStats(String keyword, String city, String education, String experience);

    List<IndustryCountDTO> getIndustryStats(String keyword, String city, String education, String experience);

    Long getTotalCount(String keyword, String city, String education, String experience);
}
