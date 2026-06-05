package com.jobdata.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.dto.Result;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.entity.UserJobHistory;
import com.jobdata.service.UserFavoriteJobService;
import com.jobdata.service.UserJobHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户行为接口：管理用户收藏与浏览历史数据。
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserActivityController {

    @Autowired
    private UserFavoriteJobService userFavoriteJobService;

    @Autowired
    private UserJobHistoryService userJobHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取用户收藏列表。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @return 收藏列表
     */
    @GetMapping("/favorites")
    public Result<List<UserFavoriteJob>> listFavorites(Authentication authentication) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }
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
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }
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
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }
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
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }
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
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }
        String sourceTable = safeString(payload.get("sourceTable"));

        Map<String, Object> job = payload.get("job") instanceof Map ? (Map<String, Object>) payload.get("job") : null;
        String jobUrl = job != null ? safeString(job.get("jobUrl")) : null;
        if (jobUrl == null || jobUrl.isBlank()) {
            jobUrl = job != null ? safeString(job.get("job_url")) : null;
        }
        if (jobUrl == null || jobUrl.isBlank()) {
            jobUrl = safeString(payload.get("jobUrl"));
        }
        if (sourceTable == null || sourceTable.isBlank() || jobUrl == null || jobUrl.isBlank()) {
            return Result.fail("缺少 sourceTable 或 jobUrl");
        }

        UserJobHistory row = userJobHistoryService.getOne(
                new LambdaQueryWrapper<UserJobHistory>()
                        .eq(UserJobHistory::getUserId, userId)
                        .eq(UserJobHistory::getSourceTable, sourceTable)
                        .eq(UserJobHistory::getJobUrl, jobUrl)
                        .last("limit 1")
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
     * 批量删除浏览历史（按历史记录 ID 列表删除）。
     *
     * @param authentication 当前认证信息（principal 为用户 ID）
     * @param payload 请求体（包含 ids 字段）
     * @return 是否删除成功
     */
    @PostMapping("/job-history/batch-delete")
    public Result<Boolean> batchDeleteJobHistory(Authentication authentication, @RequestBody Map<String, Object> payload) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }
        Object idsObj = payload == null ? null : payload.get("ids");
        if (!(idsObj instanceof List)) {
            return Result.fail("缺少 ids");
        }
        List<?> raw = (List<?>) idsObj;
        List<Long> ids = new ArrayList<>();
        for (Object v : raw) {
            Long id = safeLong(v);
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return Result.fail("ids 不能为空");
        }
        boolean ok = userJobHistoryService.remove(
                new LambdaQueryWrapper<UserJobHistory>()
                        .eq(UserJobHistory::getUserId, userId)
                        .in(UserJobHistory::getId, ids)
        );
        return Result.success(ok);
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
