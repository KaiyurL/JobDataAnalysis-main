
package com.jobdata.controller;

import com.jobdata.dto.Result;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.entity.UserJobHistory;
import com.jobdata.entity.UserProfile;
import com.jobdata.service.UserFavoriteJobService;
import com.jobdata.service.UserJobHistoryService;
import com.jobdata.service.UserProfileService;
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

/**
 * 用户接口：管理用户画像、收藏、浏览历史与匹配历史等数据。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private UserFavoriteJobService userFavoriteJobService;
    @Autowired
    private UserJobHistoryService userJobHistoryService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取当前用户的画像信息（含简历元信息与扩展信息）。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @return 用户画像数据
     */
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

    /**
     * 新增或更新当前用户画像信息。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @param payload 请求体（可包含 profile、resumeMeta、profileExtra 字段）
     * @return 更新后的用户画像数据
     */
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

    /**
     * 获取用户收藏列表。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @return 收藏列表
     */
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

    /**
     * 添加收藏（如已存在则返回已存在记录）。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @param payload 收藏数据（包含 sourceTable/jobUrl 等字段）
     * @return 收藏记录
     */
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

    /**
     * 取消收藏。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @param sourceTable 来源表
     * @param jobUrl 职位链接
     * @return 是否删除成功
     */
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

    /**
     * 获取用户职位浏览历史。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @param size 返回条数（默认 50，最大 200）
     * @return 浏览历史列表
     */
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

    /**
     * 记录用户浏览的职位信息（同一 sourceTable+jobUrl 会进行 upsert）。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @param payload 职位数据（包含 sourceTable/jobUrl 等字段）
     * @return 浏览历史记录
     */
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

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 任意对象
     * @return JSON 字符串（失败返回 null）
     */
    private String writeJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 读取 JSON 字符串为 Map。
     *
     * @param json JSON 字符串
     * @return Map（失败返回空 Map）
     */
    private Map<String, Object> readJsonAsMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 读取 JSON 字符串为任意对象（Map/List/基本类型等）。
     *
     * @param json JSON 字符串
     * @return 反序列化结果（失败返回 null）
     */
    private Object readJsonAsObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全转换为字符串并做 trim。
     *
     * @param v 原始值
     * @return 字符串（可能为 null）
     */
    private String safeString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s == null ? null : s.trim();
    }

    /**
     * 安全转换为 Long。
     *
     * @param v 原始值
     * @return Long（可能为 null）
     */
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

    /**
     * 安全转换为 Integer。
     *
     * @param v 原始值
     * @return Integer（可能为 null）
     */
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
