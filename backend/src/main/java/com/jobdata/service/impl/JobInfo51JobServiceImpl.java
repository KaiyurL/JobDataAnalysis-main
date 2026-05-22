package com.jobdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.mapper.JobInfo51JobMapper;
import com.jobdata.service.JobInfo51JobService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobInfo51JobServiceImpl extends ServiceImpl<JobInfo51JobMapper, JobInfo51Job> implements JobInfo51JobService {

    @Override
    public Page<JobInfo51Job> pageQuery(Integer current, Integer size, String keyword, String city, String education, String experience) {
        Page<JobInfo51Job> page = new Page<>(current, size);
        LambdaQueryWrapper<JobInfo51Job> wrapper = buildQueryWrapper(keyword, city, education, experience);
        wrapper.orderByDesc(JobInfo51Job::getCreatedAt);
        return this.page(page, wrapper);
    }

    private LambdaQueryWrapper<JobInfo51Job> buildQueryWrapper(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo51Job> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(JobInfo51Job::getJobName, keyword).or().like(JobInfo51Job::getCompanyName, keyword));
        }
        if (StringUtils.hasText(city)) {
            wrapper.in(JobInfo51Job::getCity, Arrays.asList(city.split(",")));
        }
        if (StringUtils.hasText(education)) {
            wrapper.eq(JobInfo51Job::getEducation, education);
        }
        if (StringUtils.hasText(experience)) {
            wrapper.eq(JobInfo51Job::getExperience, experience);
        }

        return wrapper;
    }

    @Override
    public List<CitySalaryDTO> getCitySalaryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo51Job> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo51Job> list = this.list(wrapper);
        Map<String, List<JobInfo51Job>> cityMap = list.stream()
                .filter(job -> job.getSalaryAvg() != null)
                .collect(Collectors.groupingBy(JobInfo51Job::getCity));

        return cityMap.entrySet().stream().map(entry -> {
            CitySalaryDTO dto = new CitySalaryDTO();
            dto.setCity(entry.getKey());
            dto.setCount(entry.getValue().size());
            double avg = entry.getValue().stream()
                    .mapToDouble(job -> job.getSalaryAvg().doubleValue())
                    .average().orElse(0);
            dto.setAvgSalary(Math.round(avg * 100.0) / 100.0);
            return dto;
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
                .limit(20)
                .collect(Collectors.toList());
    }

    @Override
    public List<EducationSalaryDTO> getEducationSalaryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo51Job> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo51Job> list = this.list(wrapper);
        Map<String, List<JobInfo51Job>> eduMap = list.stream()
                .filter(job -> job.getSalaryAvg() != null && StringUtils.hasText(job.getEducation()))
                .collect(Collectors.groupingBy(JobInfo51Job::getEducation));

        return eduMap.entrySet().stream().map(entry -> {
            EducationSalaryDTO dto = new EducationSalaryDTO();
            dto.setEducation(entry.getKey());
            dto.setCount(entry.getValue().size());
            double avg = entry.getValue().stream()
                    .mapToDouble(job -> job.getSalaryAvg().doubleValue())
                    .average().orElse(0);
            dto.setAvgSalary(Math.round(avg * 100.0) / 100.0);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ExperienceSalaryDTO> getExperienceSalaryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo51Job> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo51Job> list = this.list(wrapper);
        Map<String, List<JobInfo51Job>> expMap = list.stream()
                .filter(job -> job.getSalaryAvg() != null && StringUtils.hasText(job.getExperience()))
                .collect(Collectors.groupingBy(JobInfo51Job::getExperience));

        return expMap.entrySet().stream().map(entry -> {
            ExperienceSalaryDTO dto = new ExperienceSalaryDTO();
            dto.setExperience(entry.getKey());
            dto.setCount(entry.getValue().size());
            double avg = entry.getValue().stream()
                    .mapToDouble(job -> job.getSalaryAvg().doubleValue())
                    .average().orElse(0);
            dto.setAvgSalary(Math.round(avg * 100.0) / 100.0);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public List<KeywordDTO> getKeywordStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo51Job> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo51Job> list = this.list(wrapper);
        Map<String, Integer> keywordCount = new HashMap<>();

        for (JobInfo51Job job : list) {
            if (StringUtils.hasText(job.getJobKeywords())) {
                String[] keywords = job.getJobKeywords().split("[,，\\s]+");
                for (String kw : keywords) {
                    if (kw.length() >= 2) {
                        keywordCount.put(kw, keywordCount.getOrDefault(kw, 0) + 1);
                    }
                }
            }
        }

        return keywordCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(50)
                .map(entry -> {
                    KeywordDTO dto = new KeywordDTO();
                    dto.setKeyword(entry.getKey());
                    dto.setCount(entry.getValue());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<IndustryCountDTO> getIndustryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo51Job> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo51Job> list = this.list(wrapper);
        Map<String, List<JobInfo51Job>> industryMap = list.stream()
                .filter(job -> StringUtils.hasText(job.getCompanyIndustry()))
                .collect(Collectors.groupingBy(JobInfo51Job::getCompanyIndustry));

        return industryMap.entrySet().stream().map(entry -> {
            IndustryCountDTO dto = new IndustryCountDTO();
            dto.setIndustry(entry.getKey());
            dto.setCount(entry.getValue().size());
            return dto;
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
                .limit(15)
                .collect(Collectors.toList());
    }

    @Override
    public Long getTotalCount(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo51Job> wrapper = buildQueryWrapper(keyword, city, education, experience);
        return this.count(wrapper);
    }
}
