
package com.jobdata.controller;

import com.jobdata.dto.LoginResponse;
import com.jobdata.dto.Result;
import com.jobdata.entity.User;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.entity.UserJobHistory;
import com.jobdata.entity.UserMatchHistory;
import com.jobdata.entity.UserProfile;
import com.jobdata.service.UserFavoriteJobService;
import com.jobdata.service.UserJobHistoryService;
import com.jobdata.service.UserMatchHistoryService;
import com.jobdata.service.UserProfileService;
import com.jobdata.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private UserFavoriteJobService userFavoriteJobService;
    @Autowired
    private UserMatchHistoryService userMatchHistoryService;
    @Autowired
    private UserJobHistoryService userJobHistoryService;
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/info")
    public Result<LoginResponse.UserInfo> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = userService.getById(userId);
        if (user != null) {
            return Result.success(new LoginResponse.UserInfo(user.getId(), user.getUsername(), user.getRole()));
        }
        return Result.fail("用户不存在");
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserProfile row = userProfileService.getOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        Map<String, Object> out = new HashMap<>();
        if (row == null) {
            out.put("profile", new HashMap<>());
            out.put("resumeMeta", null);
            out.put("profileExtra", new HashMap<>());
            return Result.success(out);
        }
        out.put("profile", readJsonAsMap(row.getProfileJson()));
        out.put("resumeMeta", readJsonAsObject(row.getResumeMetaJson()));
        out.put("profileExtra", readJsonAsObject(row.getProfileExtraJson()));
        out.put("updatedAt", row.getUpdatedAt());
        return Result.success(out);
    }

    @PutMapping("/profile")
    public Result<Map<String, Object>> upsertProfile(Authentication authentication, @RequestBody Map<String, Object> payload) {
        Long userId = (Long) authentication.getPrincipal();

        Object profileObj = payload.containsKey("profile") ? payload.get("profile") : payload;
        Object resumeMetaObj = payload.get("resumeMeta");
        Object profileExtraObj = payload.get("profileExtra");

        UserProfile row = userProfileService.getOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new UserProfile();
            row.setUserId(userId);
            row.setCreatedAt(now);
        }
        row.setUpdatedAt(now);
        row.setProfileJson(writeJson(profileObj));
        row.setResumeMetaJson(writeJson(resumeMetaObj));
        row.setProfileExtraJson(writeJson(profileExtraObj));
        userProfileService.saveOrUpdate(row);

        Map<String, Object> out = new HashMap<>();
        out.put("profile", readJsonAsMap(row.getProfileJson()));
        out.put("resumeMeta", readJsonAsObject(row.getResumeMetaJson()));
        out.put("profileExtra", readJsonAsObject(row.getProfileExtraJson()));
        out.put("updatedAt", row.getUpdatedAt());
        return Result.success(out);
    }

    @GetMapping("/favorites")
    public Result<List<UserFavoriteJob>> listFavorites(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UserFavoriteJob> list = userFavoriteJobService.list(
                new LambdaQueryWrapper<UserFavoriteJob>()
                        .eq(UserFavoriteJob::getUserId, userId)
                        .orderByDesc(UserFavoriteJob::getCreatedAt)
        );
        return Result.success(list);
    }

    @PostMapping("/favorites")
    public Result<UserFavoriteJob> addFavorite(Authentication authentication, @RequestBody Map<String, Object> payload) {
        Long userId = (Long) authentication.getPrincipal();
        String sourceTable = safeString(payload.get("sourceTable"));

        Map<String, Object> job = payload.get("job") instanceof Map ? (Map<String, Object>) payload.get("job") : null;
        String jobUrl = job != null ? safeString(job.get("jobUrl")) : safeString(payload.get("jobUrl"));
        if (sourceTable == null || sourceTable.isBlank() || jobUrl == null || jobUrl.isBlank()) {
            return Result.fail("缺少 sourceTable 或 jobUrl");
        }

        UserFavoriteJob existed = userFavoriteJobService.getOne(
                new LambdaQueryWrapper<UserFavoriteJob>()
                        .eq(UserFavoriteJob::getUserId, userId)
                        .eq(UserFavoriteJob::getSourceTable, sourceTable)
                        .eq(UserFavoriteJob::getJobUrl, jobUrl)
        );
        if (existed != null) {
            return Result.success(existed);
        }

        UserFavoriteJob row = new UserFavoriteJob();
        row.setUserId(userId);
        row.setSourceTable(sourceTable);
        row.setJobUrl(jobUrl);
        row.setJobId(job != null ? safeLong(job.get("id")) : safeLong(payload.get("jobId")));
        row.setJobName(job != null ? safeString(job.get("jobName")) : safeString(payload.get("jobName")));
        row.setCompanyName(job != null ? safeString(job.get("companyName")) : safeString(payload.get("companyName")));
        row.setCity(job != null ? safeString(job.get("city")) : safeString(payload.get("city")));
        row.setSalaryMin(job != null ? safeInt(job.get("salaryMin")) : safeInt(payload.get("salaryMin")));
        row.setSalaryMax(job != null ? safeInt(job.get("salaryMax")) : safeInt(payload.get("salaryMax")));
        row.setExperience(job != null ? safeString(job.get("experience")) : safeString(payload.get("experience")));
        row.setEducation(job != null ? safeString(job.get("education")) : safeString(payload.get("education")));
        row.setJobJson(writeJson(job != null ? job : payload.get("jobJson")));
        row.setCreatedAt(LocalDateTime.now());
        userFavoriteJobService.save(row);
        return Result.success(row);
    }

    @DeleteMapping("/favorites")
    public Result<Boolean> removeFavorite(Authentication authentication, @RequestParam String sourceTable, @RequestParam String jobUrl) {
        Long userId = (Long) authentication.getPrincipal();
        boolean ok = userFavoriteJobService.remove(
                new LambdaQueryWrapper<UserFavoriteJob>()
                        .eq(UserFavoriteJob::getUserId, userId)
                        .eq(UserFavoriteJob::getSourceTable, sourceTable)
                        .eq(UserFavoriteJob::getJobUrl, jobUrl)
        );
        return Result.success(ok);
    }

    @GetMapping("/job-history")
    public Result<List<UserJobHistory>> listJobHistory(Authentication authentication, @RequestParam(defaultValue = "50") Integer size) {
        Long userId = (Long) authentication.getPrincipal();
        int limit = size == null ? 50 : Math.max(1, Math.min(200, size));
        List<UserJobHistory> list = userJobHistoryService.list(
                new LambdaQueryWrapper<UserJobHistory>()
                        .eq(UserJobHistory::getUserId, userId)
                        .orderByDesc(UserJobHistory::getUpdatedAt)
                        .last("limit " + limit)
        );
        return Result.success(list);
    }

    @PostMapping("/job-history")
    public Result<UserJobHistory> recordJobHistory(Authentication authentication, @RequestBody Map<String, Object> payload) {
        Long userId = (Long) authentication.getPrincipal();
        String sourceTable = safeString(payload.get("sourceTable"));

        Map<String, Object> job = payload.get("job") instanceof Map ? (Map<String, Object>) payload.get("job") : null;
        String jobUrl = job != null ? safeString(job.get("jobUrl")) : safeString(payload.get("jobUrl"));
        if (sourceTable == null || sourceTable.isBlank() || jobUrl == null || jobUrl.isBlank()) {
            return Result.fail("缺少 sourceTable 或 jobUrl");
        }

        UserJobHistory row = userJobHistoryService.getOne(
                new LambdaQueryWrapper<UserJobHistory>()
                        .eq(UserJobHistory::getUserId, userId)
                        .eq(UserJobHistory::getSourceTable, sourceTable)
                        .eq(UserJobHistory::getJobUrl, jobUrl)
        );
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new UserJobHistory();
            row.setUserId(userId);
            row.setSourceTable(sourceTable);
            row.setJobUrl(jobUrl);
            row.setCreatedAt(now);
        }
        row.setUpdatedAt(now);
        row.setJobName(job != null ? safeString(job.get("jobName")) : safeString(payload.get("jobName")));
        row.setCompanyName(job != null ? safeString(job.get("companyName")) : safeString(payload.get("companyName")));
        row.setCity(job != null ? safeString(job.get("city")) : safeString(payload.get("city")));
        row.setSalaryMin(job != null ? safeInt(job.get("salaryMin")) : safeInt(payload.get("salaryMin")));
        row.setSalaryMax(job != null ? safeInt(job.get("salaryMax")) : safeInt(payload.get("salaryMax")));
        row.setExperience(job != null ? safeString(job.get("experience")) : safeString(payload.get("experience")));
        row.setEducation(job != null ? safeString(job.get("education")) : safeString(payload.get("education")));
        row.setJobJson(writeJson(job != null ? job : payload.get("jobJson")));
        userJobHistoryService.saveOrUpdate(row);
        return Result.success(row);
    }

    @GetMapping("/match-history")
    public Result<List<UserMatchHistory>> listMatchHistory(Authentication authentication, @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) authentication.getPrincipal();
        int limit = size == null ? 20 : Math.max(1, Math.min(100, size));
        List<UserMatchHistory> list = userMatchHistoryService.list(
                new LambdaQueryWrapper<UserMatchHistory>()
                        .eq(UserMatchHistory::getUserId, userId)
                        .orderByDesc(UserMatchHistory::getCreatedAt)
                        .last("limit " + limit)
        );
        return Result.success(list);
    }

    @GetMapping("/match-history/{id}")
    public Result<Map<String, Object>> getMatchHistoryDetail(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        UserMatchHistory row = userMatchHistoryService.getOne(
                new LambdaQueryWrapper<UserMatchHistory>()
                        .eq(UserMatchHistory::getId, id)
                        .eq(UserMatchHistory::getUserId, userId)
        );
        if (row == null) {
            return Result.fail("记录不存在");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("id", row.getId());
        out.put("targetRole", row.getTargetRole());
        out.put("city", row.getCity());
        out.put("createdAt", row.getCreatedAt());
        out.put("profile", readJsonAsObject(row.getProfileJson()));
        out.put("result", readJsonAsObject(row.getResultJson()));
        return Result.success(out);
    }

    private String writeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> readJsonAsMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Object readJsonAsObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s == null ? null : s.trim();
    }

    private Long safeLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer safeInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }
}
