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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements JobInfoService {

    @Autowired
    private JobInfo51JobService jobInfo51JobService;

    @Value("${bailian.apiKey:${AI_DASHSCOPE_API_KEY:}}")
    private String apiKeyFromConfig;

    @Value("${bailian.baseUrl:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String bailianBaseUrl;

    @Value("${bailian.model:qwen3.5-flash}")
    private String bailianModel;

    @Value("${bailian.timeoutMs:60000}")
    private Integer bailianTimeoutMs;

    @Value("${jobmatch.aiRerank:false}")
    private boolean aiRerankEnabled;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object SEMANTIC_INDEX_LOCK = new Object();
    private static volatile SemanticIndex SEMANTIC_INDEX = null;
    private static volatile SemanticIndexBuild SEMANTIC_INDEX_BUILD = null;

    private static final ExecutorService SEMANTIC_INDEX_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "jobmatch-semantic-index");
            t.setDaemon(true);
            return t;
        }
    });

    private static class SemanticIndexBuild {
        final String targetUpdatedAt;
        final String bossPath;
        final String job51Path;
        volatile boolean started;

        private SemanticIndexBuild(String targetUpdatedAt, String bossPath, String job51Path) {
            this.targetUpdatedAt = targetUpdatedAt;
            this.bossPath = bossPath;
            this.job51Path = job51Path;
        }
    }

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
        wrapperBoss.orderByDesc(JobInfo::getCreatedAt);
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
        wrapper51.orderByDesc(JobInfo51Job::getCreatedAt);
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

        Set<String> queryTokens = buildQueryTokens(
                roleList,
                userSkills,
                request.getNotes(),
                request.getHighlights(),
                request.getProjects()
        );
        SemanticIndex semanticIndex = ensureSemanticIndex();

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

        YearRange userExp = parseExperienceRange(request.getExperience());
        Integer userEdu = parseEducationLevel(request.getEducation());

        List<Scored> scoredList = new ArrayList<>();

        for (Candidate c : allCandidates) {
            JobInfo job = c.job;

            String key = buildJobKey(job);
            if (StringUtils.hasText(key) && addedKeys.contains(key)) {
                continue;
            }
            if (StringUtils.hasText(key)) {
                addedKeys.add(key);
            }

            double score = 10.0;
            StringBuilder reason = new StringBuilder();

            // 0. 角色匹配分（岗位名命中目标词）
            int roleHit = 0;
            String jobNameLower = (job.getJobName() == null ? "" : job.getJobName()).toLowerCase();
            for (String r : roleList) {
                String rr = r == null ? "" : r.trim().toLowerCase();
                if (!rr.isEmpty() && jobNameLower.contains(rr)) {
                    roleHit++;
                }
            }
            if (roleHit > 0) {
                score += Math.min(15, roleHit * 6);
                reason.append("岗位词命中; ");
            }

            // 1. 经验匹配分（档位/范围匹配）
            YearRange jobExp = parseExperienceRange(job.getExperience());
            if (jobExp == null) {
                score += 8;
                reason.append("经验不限; ");
            } else if (userExp == null) {
                score += 4;
            } else if (rangesOverlap(userExp, jobExp)) {
                score += 20;
                reason.append("经验匹配; ");
            } else {
                // 不满足最低经验：直接过滤
                continue;
            }

            // 2. 学历匹配分（用户学历 >= 岗位学历）
            Integer jobEdu = parseEducationLevel(job.getEducation());
            if (jobEdu == null) {
                score += 3;
            } else if (userEdu == null) {
                score += 1;
            } else if (userEdu >= jobEdu) {
                score += 12;
                reason.append("学历满足; ");
            } else {
                continue;
            }

            // 3. 技能关键词命中分
            int skillHitCount = 0;
            if (StringUtils.hasText(job.getJobKeywords())) {
                String jobKw = job.getJobKeywords().toLowerCase();
                for (String s : userSkills) {
                    if (jobKw.contains(s)) {
                        skillHitCount++;
                        score += 8;
                    }
                }
            } else if (StringUtils.hasText(job.getJobDesc())) {
                String desc = job.getJobDesc().toLowerCase();
                for (String s : userSkills) {
                    if (desc.contains(s)) {
                        skillHitCount++;
                        score += 4;
                    }
                }
            }
            if (skillHitCount > 0) {
                score += Math.min(12, skillHitCount * 2);
                reason.append("技能命中 ").append(skillHitCount).append("; ");
            }

            // 4. 新鲜度（越新越靠前）
            int days = daysSince(job.getPublishDate(), job.getCreatedAt());
            if (days >= 0) {
                if (days <= 3) {
                    score += 10;
                    reason.append("新发布; ");
                } else if (days <= 7) {
                    score += 7;
                } else if (days <= 30) {
                    score += 3;
                }
            }

            double semanticScore = 0.0;
            if (semanticIndex != null && !queryTokens.isEmpty() && job.getId() != null) {
                SemanticMatch sm = semanticIndex.score(c.sourceTable, String.valueOf(job.getId()), queryTokens);
                if (sm != null && sm.contribution > 0) {
                    semanticScore = sm.contribution;
                    score += semanticScore;
                    if (!sm.hitTop.isEmpty()) {
                        reason.append("语义命中 ").append(String.join(" ", sm.hitTop)).append("; ");
                    } else {
                        reason.append("语义匹配; ");
                    }
                }
            }

            JobMatchDTO dto = new JobMatchDTO();
            dto.setJob(job);
            dto.setMatchScore(Math.min(99.0, score));
            dto.setMatchReason(reason.length() > 0 ? reason.substring(0, reason.length() - 2) : "匹配");
            dto.setSourceTable(c.sourceTable);
            scoredList.add(new Scored(dto, dto.getMatchScore(), semanticScore, skillHitCount, days));
        }

        // 排序：分数 -> 技能命中 -> 新鲜度 -> 创建时间
        scoredList.sort((a, b) -> {
            int c1 = Double.compare(b.score, a.score);
            if (c1 != 0) return c1;
            int cSem = Double.compare(b.semanticScore, a.semanticScore);
            if (cSem != 0) return cSem;
            int c2 = Integer.compare(b.skillHit, a.skillHit);
            if (c2 != 0) return c2;
            int c3 = Integer.compare(a.recencyDays, b.recencyDays); // 越小越新
            if (c3 != 0) return c3;
            LocalDateTime ta = a.dto.getJob() == null ? null : a.dto.getJob().getCreatedAt();
            LocalDateTime tb = b.dto.getJob() == null ? null : b.dto.getJob().getCreatedAt();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        // P2：二阶段重排默认关闭（匹配按钮后还会再走一次 AI 生成建议，避免重复调用导致延迟）

        // 多样性：同公司最多 2 条，避免“看起来全是同一家”
        Map<String, Integer> companyCounts = new HashMap<>();
        for (Scored s : scoredList) {
            JobMatchDTO dto = s.dto;
            JobInfo job = dto.getJob();
            String company = job == null ? "" : String.valueOf(job.getCompanyName());
            company = company == null ? "" : company.trim();
            int cnt = company.isEmpty() ? 0 : companyCounts.getOrDefault(company, 0);
            if (!company.isEmpty() && cnt >= 2) {
                continue;
            }
            if (!company.isEmpty()) {
                companyCounts.put(company, cnt + 1);
            }
            dto.setMatchScore(Math.round(dto.getMatchScore() * 10.0) / 10.0);
            results.add(dto);
            if (results.size() >= 30) {
                break;
            }
        }

        return results;
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

    private static class YearRange {
        final int min;
        final int max;

        private YearRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    private static class Scored {
        JobMatchDTO dto;
        double score;
        double semanticScore;
        int skillHit;
        int recencyDays;

        Scored(JobMatchDTO dto, double score, double semanticScore, int skillHit, int recencyDays) {
            this.dto = dto;
            this.score = score;
            this.semanticScore = semanticScore;
            this.skillHit = skillHit;
            this.recencyDays = recencyDays;
        }
    }

    private static YearRange parseExperienceRange(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = String.valueOf(s).trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains("不限")) {
            return null;
        }
        if (t.contains("应届") || t.contains("实习") || t.contains("在校")) {
            return new YearRange(0, 0);
        }
        String digits = t.replaceAll("\\s+", "");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\-(\\d+)年").matcher(digits);
        if (m.find()) {
            int a = safeInt(m.group(1));
            int b = safeInt(m.group(2));
            if (a >= 0 && b >= a) {
                return new YearRange(a, b);
            }
        }
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(\\d+)年").matcher(digits);
        if (m2.find()) {
            int a = safeInt(m2.group(1));
            if (a >= 0) {
                return new YearRange(a, a);
            }
        }
        return null;
    }

    private static boolean rangesOverlap(YearRange user, YearRange job) {
        if (user == null || job == null) {
            return true;
        }
        return user.max >= job.min;
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(String.valueOf(s).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static Integer parseEducationLevel(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        String t = String.valueOf(s).trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains("不限")) {
            return null;
        }
        if (t.contains("博士")) return 4;
        if (t.contains("硕士")) return 3;
        if (t.contains("本科")) return 2;
        if (t.contains("大专") || t.contains("专科")) return 1;
        return null;
    }

    private static int daysSince(LocalDate publishDate, LocalDateTime createdAt) {
        try {
            if (publishDate != null) {
                return (int) ChronoUnit.DAYS.between(publishDate, LocalDate.now());
            }
            if (createdAt != null) {
                LocalDate d = createdAt.atZone(ZoneId.systemDefault()).toLocalDate();
                return (int) ChronoUnit.DAYS.between(d, LocalDate.now());
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static final Pattern EN_TOKEN = Pattern.compile("[a-z0-9][a-z0-9.+#-]{0,30}");
    private static final Pattern ZH_TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]{2,10}");

    private static Set<String> buildQueryTokens(
            List<String> roleList,
            Set<String> userSkills,
            String notes,
            List<String> highlights,
            List<JobMatchRequest.ProjectInput> projects
    ) {
        Set<String> out = new LinkedHashSet<>();
        Set<String> stopZh = new HashSet<>(Arrays.asList(
                "开发", "工程师", "技术", "软件", "平台", "系统", "研发", "岗位", "工作", "职位", "相关"
        ));
        Set<String> stopEn = new HashSet<>(Arrays.asList(
                "and", "or", "to", "of", "the", "a", "an", "in", "on", "for", "with", "at", "by", "as"
        ));
        if (roleList != null) {
            for (String r : roleList) {
                addNormalizedToken(out, stopZh, stopEn, r);
                if (out.size() >= 80) {
                    return out;
                }
            }
        }
        if (userSkills != null) {
            for (String s : userSkills) {
                addNormalizedToken(out, stopZh, stopEn, s);
                if (out.size() >= 80) {
                    return out;
                }
            }
        }
        addFreeTextTokens(out, stopZh, stopEn, notes);
        if (out.size() >= 80) {
            return out;
        }
        if (highlights != null) {
            int max = Math.min(6, highlights.size());
            for (int i = 0; i < max; i++) {
                addFreeTextTokens(out, stopZh, stopEn, highlights.get(i));
                if (out.size() >= 80) {
                    return out;
                }
            }
        }

        if (projects != null) {
            int maxProj = Math.min(2, projects.size());
            for (int i = 0; i < maxProj; i++) {
                JobMatchRequest.ProjectInput p = projects.get(i);
                if (p == null) {
                    continue;
                }
                addFreeTextTokens(out, stopZh, stopEn, p.getName());
                addFreeTextTokens(out, stopZh, stopEn, p.getRole());
                List<String> tech = p.getTech();
                if (tech != null) {
                    int maxTech = Math.min(10, tech.size());
                    for (int j = 0; j < maxTech; j++) {
                        String t = normalizeToken(tech.get(j));
                        if (!StringUtils.hasText(t) || stopZh.contains(t) || stopEn.contains(t)) {
                            continue;
                        }
                        if (t.length() < 2 && !(t.contains("+") || t.contains("#") || t.contains("."))) {
                            continue;
                        }
                        out.add(t);
                        if (out.size() >= 80) {
                            return out;
                        }
                    }
                }
                List<String> ph = p.getHighlights();
                if (ph != null) {
                    int maxH = Math.min(4, ph.size());
                    for (int j = 0; j < maxH; j++) {
                        addFreeTextTokens(out, stopZh, stopEn, ph.get(j));
                        if (out.size() >= 80) {
                            return out;
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void addFreeTextTokens(Set<String> out, Set<String> stopZh, Set<String> stopEn, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String t = text.trim().toLowerCase();
        if (t.isEmpty()) {
            return;
        }
        Matcher m = EN_TOKEN.matcher(t);
        while (m.find()) {
            String tok = normalizeToken(m.group());
            if (!StringUtils.hasText(tok) || stopZh.contains(tok) || stopEn.contains(tok)) {
                continue;
            }
            out.add(tok);
            if (out.size() >= 80) {
                return;
            }
        }
        Matcher m2 = ZH_TOKEN.matcher(t);
        while (m2.find()) {
            String tok = m2.group();
            if (!StringUtils.hasText(tok) || stopZh.contains(tok)) {
                continue;
            }
            out.add(tok);
            if (out.size() >= 80) {
                return;
            }
        }
    }

    private static void addNormalizedToken(Set<String> out, Set<String> stopZh, Set<String> stopEn, String raw) {
        String t = normalizeToken(raw);
        if (!StringUtils.hasText(t) || stopZh.contains(t) || stopEn.contains(t)) {
            return;
        }
        if (t.length() < 2 && !(t.contains("+") || t.contains("#") || t.contains("."))) {
            return;
        }
        out.add(t);
    }

    private static String normalizeToken(String s) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim().toLowerCase();
        t = t.replace("．", ".").replace("＋", "+").replace("＃", "#");
        return t;
    }

    private static SemanticIndex ensureSemanticIndex() {
        try {
            Path cache = resolvePipelineCacheFile();
            if (cache == null || !cache.toFile().exists()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = MAPPER.readValue(cache.toFile(), Map.class);
            String updatedAt = payload.get("updatedAt") == null ? null : String.valueOf(payload.get("updatedAt"));
            SemanticIndex current = SEMANTIC_INDEX;
            if (current != null && Objects.equals(current.updatedAt, updatedAt)) {
                return current;
            }
            Object aObj = payload.get("artifacts");
            if (!(aObj instanceof Map)) {
                return current;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> artifacts = (Map<String, Object>) aObj;
            String bossPath = artifacts.get("boss_clean_nlp") == null ? null : String.valueOf(artifacts.get("boss_clean_nlp"));
            String job51Path = artifacts.get("51job_clean_nlp") == null ? null : String.valueOf(artifacts.get("51job_clean_nlp"));
            if (!StringUtils.hasText(bossPath) && !StringUtils.hasText(job51Path)) {
                return current;
            }

            scheduleSemanticIndexBuild(updatedAt, bossPath, job51Path);
            return current;
        } catch (Exception e) {
            return null;
        }
    }

    private static void scheduleSemanticIndexBuild(String updatedAt, String bossPath, String job51Path) {
        if (!StringUtils.hasText(updatedAt)) {
            return;
        }
        synchronized (SEMANTIC_INDEX_LOCK) {
            SemanticIndex current = SEMANTIC_INDEX;
            if (current != null && Objects.equals(current.updatedAt, updatedAt)) {
                return;
            }
            SemanticIndexBuild build = SEMANTIC_INDEX_BUILD;
            if (build != null && Objects.equals(build.targetUpdatedAt, updatedAt)) {
                if (!build.started) {
                    build.started = true;
                    SEMANTIC_INDEX_EXECUTOR.submit(() -> runSemanticIndexBuild(build));
                }
                return;
            }
            SemanticIndexBuild next = new SemanticIndexBuild(updatedAt, bossPath, job51Path);
            next.started = true;
            SEMANTIC_INDEX_BUILD = next;
            SEMANTIC_INDEX_EXECUTOR.submit(() -> runSemanticIndexBuild(next));
        }
    }

    private static void runSemanticIndexBuild(SemanticIndexBuild build) {
        try {
            if (build == null) {
                return;
            }
            SemanticIndex loaded = SemanticIndex.load(build.targetUpdatedAt, build.bossPath, build.job51Path);
            synchronized (SEMANTIC_INDEX_LOCK) {
                if (SEMANTIC_INDEX_BUILD != null && Objects.equals(SEMANTIC_INDEX_BUILD.targetUpdatedAt, build.targetUpdatedAt)) {
                    SEMANTIC_INDEX = loaded;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Path resolvePipelineCacheFile() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = Arrays.asList(
                current.resolve("crawler"),
                current.resolve("..").resolve("crawler"),
                current.resolve("..").resolve("..").resolve("crawler")
        );
        for (Path crawlerDir : candidates) {
            Path p = crawlerDir.resolve("output").resolve("pipeline_cache.json").toAbsolutePath().normalize();
            if (p.toFile().exists()) {
                return p;
            }
        }
        return candidates.get(0).resolve("output").resolve("pipeline_cache.json").toAbsolutePath().normalize();
    }

    private static class SemanticMatch {
        final double contribution;
        final List<String> hitTop;

        private SemanticMatch(double contribution, List<String> hitTop) {
            this.contribution = contribution;
            this.hitTop = hitTop;
        }
    }

    private static class SemanticIndex {
        final String updatedAt;
        final int docCount;
        final Map<String, String> docTokens;
        final Map<String, Integer> df;

        private SemanticIndex(String updatedAt, int docCount, Map<String, String> docTokens, Map<String, Integer> df) {
            this.updatedAt = updatedAt;
            this.docCount = docCount;
            this.docTokens = docTokens;
            this.df = df;
        }

        static SemanticIndex load(String updatedAt, String bossPath, String job51Path) throws Exception {
            Map<String, String> docTokens = new HashMap<>();
            Map<String, Integer> df = new HashMap<>();
            int n = 0;
            if (StringUtils.hasText(bossPath)) {
                n += loadCsvInto("job_info", bossPath, docTokens, df);
            }
            if (StringUtils.hasText(job51Path)) {
                n += loadCsvInto("job_info_51job", job51Path, docTokens, df);
            }
            return new SemanticIndex(updatedAt, n, docTokens, df);
        }

        SemanticMatch score(String sourceTable, String id, Set<String> queryTokens) {
            if (!StringUtils.hasText(sourceTable) || !StringUtils.hasText(id) || queryTokens == null || queryTokens.isEmpty()) {
                return null;
            }
            String key = sourceTable + ":" + id;
            String tokens = docTokens.get(key);
            if (!StringUtils.hasText(tokens)) {
                return null;
            }
            String[] parts = tokens.split("\\s+");
            if (parts.length == 0) {
                return null;
            }
            int docLen = 0;
            Map<String, Integer> hitCounts = new HashMap<>();
            for (String p : parts) {
                String t = normalizeToken(p);
                if (!StringUtils.hasText(t)) {
                    continue;
                }
                docLen++;
                if (queryTokens.contains(t)) {
                    hitCounts.put(t, hitCounts.getOrDefault(t, 0) + 1);
                }
            }
            if (docLen == 0 || hitCounts.isEmpty()) {
                return null;
            }
            double raw = 0.0;
            for (Map.Entry<String, Integer> e : hitCounts.entrySet()) {
                String tok = e.getKey();
                int tf = e.getValue();
                double tfNorm = (double) tf / (double) docLen;
                raw += tfNorm * idf(tok);
            }
            double contribution = Math.min(20.0, raw * 12.0);
            if (contribution <= 0.01) {
                return null;
            }
            List<String> top = hitCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            return new SemanticMatch(contribution, top);
        }

        private double idf(String token) {
            int d = df.getOrDefault(token, 0);
            return Math.log((docCount + 1.0) / (d + 1.0)) + 1.0;
        }

        private static int loadCsvInto(String sourceTable, String csvPath, Map<String, String> outDocTokens, Map<String, Integer> df) throws Exception {
            Path p = Paths.get(csvPath).toAbsolutePath().normalize();
            if (!p.toFile().exists()) {
                return 0;
            }
            int docCount = 0;
            try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                String headerLine = br.readLine();
                if (!StringUtils.hasText(headerLine)) {
                    return 0;
                }
                List<String> header = parseCsvLine(headerLine.replace("\uFEFF", ""));
                int idIdx = header.indexOf("id");
                int tokIdx = header.indexOf("tokens_core");
                if (idIdx < 0 || tokIdx < 0) {
                    return 0;
                }
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    List<String> cols = parseCsvLine(line);
                    if (cols.size() <= Math.max(idIdx, tokIdx)) {
                        continue;
                    }
                    String id = cols.get(idIdx);
                    String tokens = cols.get(tokIdx);
                    if (!StringUtils.hasText(id) || !StringUtils.hasText(tokens)) {
                        continue;
                    }
                    String normalized = normalizeTokens(tokens);
                    if (!StringUtils.hasText(normalized)) {
                        continue;
                    }
                    outDocTokens.put(sourceTable + ":" + id.trim(), normalized);
                    docCount++;
                    Set<String> unique = new HashSet<>();
                    for (String t : normalized.split("\\s+")) {
                        String x = normalizeToken(t);
                        if (StringUtils.hasText(x)) {
                            unique.add(x);
                        }
                    }
                    for (String t : unique) {
                        df.put(t, df.getOrDefault(t, 0) + 1);
                    }
                }
            }
            return docCount;
        }

        private static String normalizeTokens(String tokens) {
            if (!StringUtils.hasText(tokens)) {
                return "";
            }
            String[] parts = tokens.trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                String t = normalizeToken(p);
                if (!StringUtils.hasText(t)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t);
            }
            return sb.toString();
        }

        private static List<String> parseCsvLine(String line) {
            List<String> out = new ArrayList<>();
            if (line == null) {
                return out;
            }
            StringBuilder sb = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = !inQuotes;
                    }
                } else if (c == ',' && !inQuotes) {
                    out.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
            out.add(sb.toString());
            return out;
        }
    }


}
