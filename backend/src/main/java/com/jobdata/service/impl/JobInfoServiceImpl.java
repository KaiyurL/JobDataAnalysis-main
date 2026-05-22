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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements JobInfoService {

    @Autowired
    private JobInfo51JobService jobInfo51JobService;

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
    public List<CitySalaryDTO> getCitySalaryStats(String keyword, String city, String education, String experience) {
        LambdaQueryWrapper<JobInfo> wrapper = buildQueryWrapper(keyword, city, education, experience);
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
        
        List<JobInfo> bossSimilarJobs = this.list(wrapper);

        LambdaQueryWrapper<JobInfo51Job> wrapper51 = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getEducation())) {
            wrapper51.eq(JobInfo51Job::getEducation, request.getEducation());
        }
        if (StringUtils.hasText(request.getExperience())) {
            wrapper51.eq(JobInfo51Job::getExperience, request.getExperience());
        }
        if (StringUtils.hasText(request.getCity())) {
            wrapper51.eq(JobInfo51Job::getCity, request.getCity());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper51.like(JobInfo51Job::getJobName, request.getKeyword());
        }
        wrapper51.isNotNull(JobInfo51Job::getSalaryAvg);

        List<JobInfo51Job> job51SimilarJobs = jobInfo51JobService.list(wrapper51);
        
        SalaryPredictResponse response = new SalaryPredictResponse();
        
        if (bossSimilarJobs.isEmpty() && job51SimilarJobs.isEmpty()) {
            response.setSalaryMinPredicted(new java.math.BigDecimal(0));
            response.setSalaryMaxPredicted(new java.math.BigDecimal(0));
            response.setSimilarJobs(new ArrayList<>());
            return response;
        }

        List<JobInfo> similarJobs = new ArrayList<>();
        similarJobs.addAll(bossSimilarJobs);
        for (JobInfo51Job job : job51SimilarJobs) {
            JobInfo mapped = new JobInfo();
            mapped.setId(job.getId());
            mapped.setJobName(job.getJobName());
            mapped.setCompanyName(job.getCompanyName());
            mapped.setCity(job.getCity());
            mapped.setSalaryMin(job.getSalaryMin());
            mapped.setSalaryMax(job.getSalaryMax());
            mapped.setSalaryAvg(job.getSalaryAvg());
            mapped.setExperience(job.getExperience());
            mapped.setEducation(job.getEducation());
            mapped.setJobUrl(job.getJobUrl());
            similarJobs.add(mapped);
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
                .filter(job -> job.getSalaryAvg() != null)
                .sorted((a, b) -> b.getSalaryAvg().compareTo(a.getSalaryAvg()))
                .limit(5)
                .collect(Collectors.toList());
        response.setSimilarJobs(topSimilar);
        
        return response;
    }

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

    @Override
    public List<JobMatchDTO> matchJobs(JobMatchRequest request) {
        if (request == null || !StringUtils.hasText(request.getTargetRole())) {
            return new ArrayList<>();
        }

        List<String> roleList = Arrays.stream(request.getTargetRole().split("[,，/\\s]+"))
                .map(s -> s == null ? "" : s.trim())
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        List<String> cityList = new ArrayList<>();
        if (StringUtils.hasText(request.getCity())) {
            cityList = Arrays.stream(request.getCity().split("[,，/]"))
                    .map(s -> s == null ? "" : s.trim())
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
        }

        LambdaQueryWrapper<JobInfo> wrapperBoss = new LambdaQueryWrapper<>();
        if (!cityList.isEmpty()) {
            wrapperBoss.in(JobInfo::getCity, cityList);
        }
        wrapperBoss.and(w -> {
            for (String role : roleList) {
                w.or().like(JobInfo::getJobName, role);
            }
        });
        wrapperBoss.last("LIMIT 200");
        List<JobInfo> candidatesBoss = this.list(wrapperBoss);

        LambdaQueryWrapper<JobInfo51Job> wrapper51 = new LambdaQueryWrapper<>();
        if (!cityList.isEmpty()) {
            wrapper51.in(JobInfo51Job::getCity, cityList);
        }
        wrapper51.and(w -> {
            for (String role : roleList) {
                w.or().like(JobInfo51Job::getJobName, role);
            }
        });
        wrapper51.last("LIMIT 200");
        List<JobInfo51Job> candidates51 = jobInfo51JobService.list(wrapper51);

        // 获取用户技能集合
        Set<String> userSkills = new HashSet<>();
        if (StringUtils.hasText(request.getSkills())) {
            for (String s : request.getSkills().split("[,，\\s]+")) {
                if (StringUtils.hasText(s)) {
                    userSkills.add(s.trim().toLowerCase());
                }
            }
        }

        class Candidate {
            JobInfo job;
            String sourceTable;

            Candidate(JobInfo job, String sourceTable) {
                this.job = job;
                this.sourceTable = sourceTable;
            }
        }

        List<Candidate> allCandidates = new ArrayList<>();
        for (JobInfo j : candidatesBoss) {
            allCandidates.add(new Candidate(j, "job_info"));
        }
        for (JobInfo51Job j : candidates51) {
            allCandidates.add(new Candidate(toJobInfo(j), "job_info_51job"));
        }

        List<JobMatchDTO> results = new ArrayList<>();
        Set<String> addedKeys = new HashSet<>();

        for (Candidate c : allCandidates) {
            JobInfo job = c.job;

            String key = buildJobKey(job);
            if (StringUtils.hasText(key) && addedKeys.contains(key)) {
                continue;
            }
            if (StringUtils.hasText(key)) {
                addedKeys.add(key);
            }

            double score = 0.0;
            StringBuilder reason = new StringBuilder();

            // 1. 经验匹配分 (简单规则)
            if (StringUtils.hasText(request.getExperience()) && request.getExperience().equals(job.getExperience())) {
                score += 30;
                reason.append("经验要求匹配; ");
            } else if (StringUtils.hasText(job.getExperience()) && job.getExperience().contains("不限")) {
                score += 15;
                reason.append("经验不限; ");
            }

            // 2. 学历匹配分
            if (StringUtils.hasText(request.getEducation()) && request.getEducation().equals(job.getEducation())) {
                score += 20;
                reason.append("学历匹配; ");
            }

            // 3. 技能关键词命中分
            int skillHitCount = 0;
            if (StringUtils.hasText(job.getJobKeywords())) {
                String jobKw = job.getJobKeywords().toLowerCase();
                for (String s : userSkills) {
                    if (jobKw.contains(s)) {
                        skillHitCount++;
                        score += 15;
                    }
                }
            } else if (StringUtils.hasText(job.getJobDesc())) {
                String desc = job.getJobDesc().toLowerCase();
                for (String s : userSkills) {
                    if (desc.contains(s)) {
                        skillHitCount++;
                        score += 8;
                    }
                }
            }
            if (skillHitCount > 0) {
                reason.append("命中 ").append(skillHitCount).append(" 项核心技能; ");
            }

            // 如果连一点都没匹配上，说明只是强召回回来的，过滤掉
            if (score < 10 && skillHitCount == 0 && !reason.toString().contains("经验") && !reason.toString().contains("学历")) {
                continue;
            }

            JobMatchDTO dto = new JobMatchDTO();
            dto.setJob(job);
            dto.setMatchScore(Math.min(99.0, score + 10 + (Math.random() * 5))); // 基础分+扰动避免同分
            dto.setMatchReason(reason.length() > 0 ? reason.substring(0, reason.length() - 2) : "符合基础条件");
            dto.setSourceTable(c.sourceTable);
            results.add(dto);
        }

        // 按分数降序，返回 Top 30
        return results.stream()
                .sorted((a, b) -> b.getMatchScore().compareTo(a.getMatchScore()))
                .limit(30)
                .peek(dto -> dto.setMatchScore(Math.round(dto.getMatchScore() * 10.0) / 10.0)) // 保留一位小数
                .collect(Collectors.toList());
    }

    private static JobInfo toJobInfo(JobInfo51Job src) {
        JobInfo out = new JobInfo();
        out.setId(src.getId());
        out.setJobName(src.getJobName());
        out.setCompanyName(src.getCompanyName());
        out.setCity(src.getCity());
        out.setJobUrl(src.getJobUrl());
        out.setSalaryMin(src.getSalaryMin());
        out.setSalaryMax(src.getSalaryMax());
        out.setSalaryAvg(src.getSalaryAvg());
        out.setExperience(src.getExperience());
        out.setEducation(src.getEducation());
        out.setJobDesc(src.getJobDesc());
        out.setJobKeywords(src.getJobKeywords());
        out.setCompanySize(src.getCompanySize());
        out.setCompanyIndustry(src.getCompanyIndustry());
        out.setCompanyWelfare(src.getCompanyWelfare());
        out.setPublishDate(src.getPublishDate());
        out.setCreatedAt(src.getCreatedAt());
        return out;
    }

    private static String buildJobKey(JobInfo job) {
        if (job == null) return null;
        if (StringUtils.hasText(job.getJobUrl())) {
            return job.getJobUrl().trim().toLowerCase();
        }
        String a = StringUtils.hasText(job.getJobName()) ? job.getJobName().trim() : "";
        String b = StringUtils.hasText(job.getCompanyName()) ? job.getCompanyName().trim() : "";
        String c = StringUtils.hasText(job.getCity()) ? job.getCity().trim() : "";
        String key = (a + "|" + b + "|" + c).toLowerCase();
        return key.isBlank() ? null : key;
    }

}
