package com.jobdata.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo;

import java.util.List;

public interface JobInfoService extends IService<JobInfo> {

    Page<JobInfo> pageQuery(Integer current, Integer size, String keyword, String city, String education, String experience);

    List<CitySalaryDTO> getCitySalaryStats(String keyword, String education, String experience);

    List<EducationSalaryDTO> getEducationSalaryStats(String keyword, String education, String experience);

    List<ExperienceSalaryDTO> getExperienceSalaryStats(String keyword, String education, String experience);

    List<KeywordDTO> getKeywordStats(String keyword, String city, String education, String experience);

    List<IndustryCountDTO> getIndustryStats(String keyword, String city, String education, String experience);

    Long getTotalCount(String keyword, String city, String education, String experience);

    SalaryPredictResponse predictSalary(SalaryPredictRequest request);

    List<JobMatchResponse> matchJobs(JobMatchRequest request);

    List<String> getAllSkills();

    List<CompanyHotDTO> getCompanyHotStats();

    List<CompanySalaryDTO> getCompanySalaryStats();

    List<CompanySizeDTO> getCompanySizeStats();

}
