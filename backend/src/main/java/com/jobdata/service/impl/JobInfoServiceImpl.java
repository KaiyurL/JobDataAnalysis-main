package com.jobdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.mapper.JobInfoMapper;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 职位数据服务实现：基于 MyBatis-Plus 完成职位查询，并在内存中做统计聚合。
 */
@Service
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements JobInfoService {

    @Autowired
    private JobInfo51JobService jobInfo51JobService;

    /**
     * 分页查询职位信息。
     */
    @Override
    public Page<JobInfo> pageQuery(Integer current, Integer size, String keyword, String city, String education, String experience) {
        Page<JobInfo> page = new Page<>(current, size);
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        wrapper.orderByDesc(JobInfo::getCreatedAt);
        return this.page(page, wrapper);
    }

    /**
     * 构造职位查询条件包装器。
     *
     * @param keyword 关键词（可选）
     * @param city 城市（可选，逗号分隔）
     * @param education 学历（可选）
     * @param experience 经验（可选）
     * @return 查询条件
     */
    private LambdaQueryWrapper<JobInfo> buildQueryWrapper(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(JobInfo::getJobName, keyword).or().like(JobInfo::getCompanyName, keyword));
        }
        if (StringUtils.hasText(city)) {
            wrapper.in(JobInfo::getCity, Arrays.asList(city.split(",")));
        }
        if (StringUtils.hasText(education)) {
            wrapper.eq(JobInfo::getEducation, education);
        }
        if (StringUtils.hasText(experience)) {
            wrapper.eq(JobInfo::getExperience, experience);
        }

        return wrapper;
    }

    /**
     * 按城市统计职位数量与平均薪资。
     */
    @Override
    public List<CitySalaryDTO> getCitySalaryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo> list = this.list(wrapper);
        Map<String, List<JobInfo>> cityMap = list.stream()
                .filter(job -> job.getSalaryAvg() != null && StringUtils.hasText(job.getCity()))
                .collect(Collectors.groupingBy(JobInfo::getCity));

        return cityMap.entrySet().stream().map(entry -> {
            CitySalaryDTO dto = new CitySalaryDTO();
            dto.setCity(entry.getKey());
            dto.setCount(entry.getValue().size());
            double avg = entry.getValue().stream()
                    .mapToDouble(job -> job.getSalaryAvg().doubleValue())
                    .average().orElse(0);
            dto.setAvgSalary(Math.round(avg * 100.0) / 100.0);
            return dto;
        }).sorted((a, b) -> {
            int c1 = Double.compare(b.getAvgSalary(), a.getAvgSalary());
            if (c1 != 0) return c1;
            return b.getCount().compareTo(a.getCount());
        }).collect(Collectors.toList());
    }

    /**
     * 按学历统计职位数量与平均薪资。
     */
    @Override
    public List<EducationSalaryDTO> getEducationSalaryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo> list = this.list(wrapper);
        Map<String, List<JobInfo>> eduMap = list.stream()
                .filter(job -> job.getSalaryAvg() != null && StringUtils.hasText(job.getEducation()))
                .collect(Collectors.groupingBy(JobInfo::getEducation));

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

    /**
     * 按经验统计职位数量与平均薪资。
     */
    @Override
    public List<ExperienceSalaryDTO> getExperienceSalaryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo> list = this.list(wrapper);
        Map<String, List<JobInfo>> expMap = list.stream()
                .filter(job -> job.getSalaryAvg() != null && StringUtils.hasText(job.getExperience()))
                .collect(Collectors.groupingBy(JobInfo::getExperience));

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

    /**
     * 统计职位关键词出现次数。
     */
    @Override
    public List<KeywordDTO> getKeywordStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo> list = this.list(wrapper);
        Map<String, Integer> keywordCount = new HashMap<>();

        for (JobInfo job : list) {
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

    /**
     * 按行业统计职位数量。
     */
    @Override
    public List<IndustryCountDTO> getIndustryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        List<JobInfo> list = this.list(wrapper);
        Map<String, List<JobInfo>> industryMap = list.stream()
                .filter(job -> StringUtils.hasText(job.getCompanyIndustry()))
                .collect(Collectors.groupingBy(JobInfo::getCompanyIndustry));

        return industryMap.entrySet().stream().map(entry -> {
            IndustryCountDTO dto = new IndustryCountDTO();
            dto.setIndustry(entry.getKey());
            dto.setCount(entry.getValue().size());
            return dto;
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
          .limit(15)
          .collect(Collectors.toList());
    }

    /**
     * 获取符合条件的职位总量。
     */
    @Override
    public Long getTotalCount(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        return this.count(wrapper);
    }

    /**
     * 获取热门公司统计（聚合多个职位来源）。
     */
    @Override
    public List<CompanyHotDTO> getCompanyHotStats() {
        Map<String, Integer> companyCount = new HashMap<>();

        for (JobInfo job : this.list()) {
            if (StringUtils.hasText(job.getCompanyName())) {
                companyCount.put(job.getCompanyName(), companyCount.getOrDefault(job.getCompanyName(), 0) + 1);
            }
        }
        for (JobInfo51Job job : jobInfo51JobService.list()) {
            if (StringUtils.hasText(job.getCompanyName())) {
                companyCount.put(job.getCompanyName(), companyCount.getOrDefault(job.getCompanyName(), 0) + 1);
            }
        }

        return companyCount.entrySet().stream().map(entry -> {
            CompanyHotDTO dto = new CompanyHotDTO();
            dto.setCompanyName(entry.getKey());
            dto.setCount(entry.getValue());
            return dto;
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
          .limit(10)
          .collect(Collectors.toList());
    }

    /**
     * 获取公司平均薪资统计（聚合多个职位来源）。
     */
    @Override
    public List<CompanySalaryDTO> getCompanySalaryStats() {
        Map<String, double[]> agg = new HashMap<>();

        for (JobInfo job : this.list()) {
            if (StringUtils.hasText(job.getCompanyName()) && job.getSalaryAvg() != null) {
                double[] v = agg.computeIfAbsent(job.getCompanyName(), k -> new double[]{0, 0});
                v[0] += job.getSalaryAvg().doubleValue();
                v[1] += 1;
            }
        }
        for (JobInfo51Job job : jobInfo51JobService.list()) {
            if (StringUtils.hasText(job.getCompanyName()) && job.getSalaryAvg() != null) {
                double[] v = agg.computeIfAbsent(job.getCompanyName(), k -> new double[]{0, 0});
                v[0] += job.getSalaryAvg().doubleValue();
                v[1] += 1;
            }
        }

        return agg.entrySet().stream()
                .filter(entry -> entry.getValue()[1] >= 2)
                .map(entry -> {
                    CompanySalaryDTO dto = new CompanySalaryDTO();
                    dto.setCompanyName(entry.getKey());
                    double avg = entry.getValue()[0] / entry.getValue()[1];
                    dto.setAvgSalary(Math.round(avg * 100.0) / 100.0);
                    return dto;
                })
                .sorted((a, b) -> Double.compare(b.getAvgSalary(), a.getAvgSalary()))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * 获取公司规模分布统计（聚合多个职位来源）。
     */
    @Override
    public List<CompanySizeDTO> getCompanySizeStats() {
        Map<String, Integer> sizeCount = new HashMap<>();

        for (JobInfo job : this.list()) {
            if (StringUtils.hasText(job.getCompanySize())) {
                sizeCount.put(job.getCompanySize(), sizeCount.getOrDefault(job.getCompanySize(), 0) + 1);
            }
        }
        for (JobInfo51Job job : jobInfo51JobService.list()) {
            if (StringUtils.hasText(job.getCompanySize())) {
                sizeCount.put(job.getCompanySize(), sizeCount.getOrDefault(job.getCompanySize(), 0) + 1);
            }
        }

        return sizeCount.entrySet().stream().map(entry -> {
            CompanySizeDTO dto = new CompanySizeDTO();
            dto.setSize(entry.getKey());
            dto.setCount(entry.getValue());
            return dto;
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
          .collect(Collectors.toList());
    }
}
