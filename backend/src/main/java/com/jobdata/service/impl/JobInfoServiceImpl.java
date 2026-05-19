package com.jobdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobdata.dto.*;
import com.jobdata.entity.JobInfo;
import com.jobdata.mapper.JobInfoMapper;
import com.jobdata.service.JobInfoService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements JobInfoService {

    @Override
    public Page<JobInfo> pageQuery(Integer current, Integer size, String keyword, String city, String education, String experience) {
        Page<JobInfo> page = new Page<>(current, size);
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        wrapper.orderByDesc(JobInfo::getCreatedAt);
        return this.page(page, wrapper);
    }

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

    @Override
    public List<CitySalaryDTO> getCitySalaryStats(String keyword, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, null, education, experience);
        List<JobInfo> list = this.list(wrapper);
        Map<String, List<JobInfo>> cityMap = list.stream()
                .filter(job -> job.getSalaryAvg() != null)
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
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
          .limit(20)
          .collect(Collectors.toList());
    }

    @Override
    public List<EducationSalaryDTO> getEducationSalaryStats(String keyword, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, null, null, experience);
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

    @Override
    public List<ExperienceSalaryDTO> getExperienceSalaryStats(String keyword, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, null, education, null);
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

    @Override
    public Long getTotalCount(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
        return this.count(wrapper);
    }

    @Override
    public SalaryPredictResponse predictSalary(SalaryPredictRequest request) {
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(request.getEducation())) {
            wrapper.eq(JobInfo::getEducation, request.getEducation());
        }
        if (StringUtils.hasText(request.getExperience())) {
            wrapper.eq(JobInfo::getExperience, request.getExperience());
        }
        if (StringUtils.hasText(request.getCity())) {
            wrapper.eq(JobInfo::getCity, request.getCity());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.like(JobInfo::getJobName, request.getKeyword());
        }
        
        wrapper.isNotNull(JobInfo::getSalaryAvg);
        
        List<JobInfo> similarJobs = this.list(wrapper);
        
        SalaryPredictResponse response = new SalaryPredictResponse();
        
        if (similarJobs.isEmpty()) {
            response.setSalaryMinPredicted(new java.math.BigDecimal(0));
            response.setSalaryMaxPredicted(new java.math.BigDecimal(0));
            response.setSimilarJobs(new ArrayList<>());
            return response;
        }
        
        List<Double> salaries = similarJobs.stream()
                .filter(job -> job.getSalaryAvg() != null)
                .map(job -> job.getSalaryAvg().doubleValue())
                .collect(Collectors.toList());
        
        double mean = salaries.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = salaries.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        double minPred = Math.max(0, mean - stdDev);
        double maxPred = mean + stdDev;
        
        response.setSalaryMinPredicted(new java.math.BigDecimal(Math.round(minPred * 100.0) / 100.0));
        response.setSalaryMaxPredicted(new java.math.BigDecimal(Math.round(maxPred * 100.0) / 100.0));
        
        List<JobInfo> topSimilar = similarJobs.stream()
                .sorted((a, b) -> b.getSalaryAvg().compareTo(a.getSalaryAvg()))
                .limit(5)
                .collect(Collectors.toList());
        response.setSimilarJobs(topSimilar);
        
        return response;
    }

    @Override
    public List<JobMatchResponse> matchJobs(JobMatchRequest request) {
        List<JobInfo> allJobs = this.list();
        Set<String> userSkills = new HashSet<>(request.getSkills());
        
        List<JobMatchResponse> matches = new ArrayList<>();
        
        for (JobInfo job : allJobs) {
            Set<String> jobSkills = new HashSet<>();
            if (StringUtils.hasText(job.getJobKeywords())) {
                String[] keywords = job.getJobKeywords().split("[,，\\s]+");
                for (String kw : keywords) {
                    if (kw.length() >= 2) {
                        jobSkills.add(kw);
                    }
                }
            }
            
            if (jobSkills.isEmpty()) {
                continue;
            }
            
            Set<String> intersection = new HashSet<>(jobSkills);
            intersection.retainAll(userSkills);
            
            double matchScore = (double) intersection.size() / jobSkills.size();
            
            if (matchScore > 0) {
                JobMatchResponse resp = new JobMatchResponse();
                resp.setId(job.getId());
                resp.setJobName(job.getJobName());
                resp.setCompanyName(job.getCompanyName());
                resp.setSalaryMin(job.getSalaryMin());
                resp.setSalaryMax(job.getSalaryMax());
                resp.setSalaryAvg(job.getSalaryAvg());
                resp.setCity(job.getCity());
                resp.setMatchScore(matchScore);
                matches.add(resp);
            }
        }
        
        return matches.stream()
                .sorted((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()))
                .limit(10)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getAllSkills() {
        List<JobInfo> allJobs = this.list();
        Map<String, Integer> skillCount = new HashMap<>();
        
        for (JobInfo job : allJobs) {
            if (StringUtils.hasText(job.getJobKeywords())) {
                String[] keywords = job.getJobKeywords().split("[,，\\s]+");
                for (String kw : keywords) {
                    if (kw.length() >= 2) {
                        skillCount.put(kw, skillCount.getOrDefault(kw, 0) + 1);
                    }
                }
            }
        }
        
        return skillCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public List<CompanyHotDTO> getCompanyHotStats() {
        List<JobInfo> allJobs = this.list();
        Map<String, List<JobInfo>> companyMap = allJobs.stream()
                .filter(job -> StringUtils.hasText(job.getCompanyName()))
                .collect(Collectors.groupingBy(JobInfo::getCompanyName));
        
        return companyMap.entrySet().stream().map(entry -> {
            CompanyHotDTO dto = new CompanyHotDTO();
            dto.setCompanyName(entry.getKey());
            dto.setCount(entry.getValue().size());
            return dto;
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
          .limit(10)
          .collect(Collectors.toList());
    }

    @Override
    public List<CompanySalaryDTO> getCompanySalaryStats() {
        List<JobInfo> allJobs = this.list();
        Map<String, List<JobInfo>> companyMap = allJobs.stream()
                .filter(job -> StringUtils.hasText(job.getCompanyName()) && job.getSalaryAvg() != null)
                .collect(Collectors.groupingBy(JobInfo::getCompanyName));
        
        return companyMap.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)  // 至少有2个岗位才统计
                .map(entry -> {
                    CompanySalaryDTO dto = new CompanySalaryDTO();
                    dto.setCompanyName(entry.getKey());
                    double avg = entry.getValue().stream()
                            .mapToDouble(job -> job.getSalaryAvg().doubleValue())
                            .average().orElse(0);
                    dto.setAvgSalary(Math.round(avg * 100.0) / 100.0);
                    return dto;
                }).sorted((a, b) -> Double.compare(b.getAvgSalary(), a.getAvgSalary()))
                  .limit(10)
                  .collect(Collectors.toList());
    }

    @Override
    public List<CompanySizeDTO> getCompanySizeStats() {
        List<JobInfo> allJobs = this.list();
        Map<String, List<JobInfo>> sizeMap = allJobs.stream()
                .filter(job -> StringUtils.hasText(job.getCompanySize()))
                .collect(Collectors.groupingBy(JobInfo::getCompanySize));
        
        return sizeMap.entrySet().stream().map(entry -> {
            CompanySizeDTO dto = new CompanySizeDTO();
            dto.setSize(entry.getKey());
            dto.setCount(entry.getValue().size());
            return dto;
        }).sorted((a, b) -> b.getCount().compareTo(a.getCount()))
          .collect(Collectors.toList());
    }

}
