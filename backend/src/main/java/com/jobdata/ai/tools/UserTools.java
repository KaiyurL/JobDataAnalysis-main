package com.jobdata.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.context.UserContextHolder;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.entity.UserJobHistory;
import com.jobdata.entity.UserMatchHistory;
import com.jobdata.entity.UserProfile;
import com.jobdata.service.UserFavoriteJobService;
import com.jobdata.service.UserJobHistoryService;
import com.jobdata.service.UserMatchHistoryService;
import com.jobdata.service.UserProfileService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class UserTools {

    private final UserProfileService userProfileService;
    private final UserFavoriteJobService userFavoriteJobService;
    private final UserJobHistoryService userJobHistoryService;
    private final UserMatchHistoryService userMatchHistoryService;
    private final ObjectMapper objectMapper;

    public UserTools(
            UserProfileService userProfileService,
            UserFavoriteJobService userFavoriteJobService,
            UserJobHistoryService userJobHistoryService,
            UserMatchHistoryService userMatchHistoryService,
            ObjectMapper objectMapper
    ) {
        this.userProfileService = userProfileService;
        this.userFavoriteJobService = userFavoriteJobService;
        this.userJobHistoryService = userJobHistoryService;
        this.userMatchHistoryService = userMatchHistoryService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "user_get_profile", description = "读取当前登录用户的画像（包含 profile/resumeMeta/profileExtra）。当用户询问“我的画像是什么/我之前填了什么/按我的情况推荐”时使用。")
    public Map<String, Object> userGetProfile() {
        Long userId = requireUserId();
        UserProfile row = userProfileService.getOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        Map<String, Object> out = new HashMap<>();
        if (row == null) {
            out.put("profile", new HashMap<>());
            out.put("resumeMeta", null);
            out.put("profileExtra", new HashMap<>());
            return out;
        }
        out.put("profile", readJsonAsMap(row.getProfileJson()));
        out.put("resumeMeta", readJsonAsObject(row.getResumeMetaJson()));
        out.put("profileExtra", readJsonAsObject(row.getProfileExtraJson()));
        out.put("updatedAt", row.getUpdatedAt());
        return out;
    }

    @Tool(name = "user_upsert_profile", description = "更新当前登录用户画像（仅当用户明确要求修改画像/保存偏好时使用）。")
    public Map<String, Object> userUpsertProfile(
            @ToolParam(description = "画像对象（建议包含 targetRole/city/education/experience/skills/notes 等字段）") Map<String, Object> profile,
            @ToolParam(description = "简历元信息，可为空", required = false) Map<String, Object> resumeMeta,
            @ToolParam(description = "额外信息，可为空", required = false) Map<String, Object> profileExtra
    ) {
        Long userId = requireUserId();

        UserProfile row = userProfileService.getOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        LocalDateTime now = LocalDateTime.now();
        if (row == null) {
            row = new UserProfile();
            row.setUserId(userId);
            row.setCreatedAt(now);
        }
        row.setUpdatedAt(now);
        row.setProfileJson(writeJson(profile));
        row.setResumeMetaJson(writeJson(resumeMeta));
        row.setProfileExtraJson(writeJson(profileExtra));
        userProfileService.saveOrUpdate(row);

        Map<String, Object> out = new HashMap<>();
        out.put("profile", readJsonAsMap(row.getProfileJson()));
        out.put("resumeMeta", readJsonAsObject(row.getResumeMetaJson()));
        out.put("profileExtra", readJsonAsObject(row.getProfileExtraJson()));
        out.put("updatedAt", row.getUpdatedAt());
        return out;
    }

    @Tool(name = "user_list_favorites", description = "列出当前用户收藏的岗位。用户问“我收藏了哪些/基于收藏推荐”时使用。")
    public List<Map<String, Object>> userListFavorites(@ToolParam(description = "返回数量上限，默认 20", required = false) Integer limit) {
        Long userId = requireUserId();
        int lim = limit == null ? 20 : Math.max(1, Math.min(200, limit));
        List<UserFavoriteJob> list = userFavoriteJobService.list(
                new LambdaQueryWrapper<UserFavoriteJob>()
                        .eq(UserFavoriteJob::getUserId, userId)
                        .orderByDesc(UserFavoriteJob::getCreatedAt)
                        .last("limit " + lim)
        );
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserFavoriteJob j : list) {
            out.add(mapFavorite(j));
        }
        return out;
    }

    @Tool(name = "user_list_job_history", description = "列出当前用户浏览/查看过的岗位历史。用户问“我最近看过什么/基于历史推荐”时使用。")
    public List<Map<String, Object>> userListJobHistory(@ToolParam(description = "返回数量上限，默认 50", required = false) Integer limit) {
        Long userId = requireUserId();
        int lim = limit == null ? 50 : Math.max(1, Math.min(200, limit));
        List<UserJobHistory> list = userJobHistoryService.list(
                new LambdaQueryWrapper<UserJobHistory>()
                        .eq(UserJobHistory::getUserId, userId)
                        .orderByDesc(UserJobHistory::getUpdatedAt)
                        .last("limit " + lim)
        );
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserJobHistory j : list) {
            out.add(mapHistory(j));
        }
        return out;
    }

    @Tool(name = "user_list_match_history", description = "列出当前用户的匹配历史记录（不包含大结果详情）。用户问“我之前匹配了什么/上次匹配结果”时使用。")
    public List<Map<String, Object>> userListMatchHistory(@ToolParam(description = "返回数量上限，默认 20", required = false) Integer limit) {
        Long userId = requireUserId();
        int lim = limit == null ? 20 : Math.max(1, Math.min(100, limit));
        List<UserMatchHistory> list = userMatchHistoryService.list(
                new LambdaQueryWrapper<UserMatchHistory>()
                        .eq(UserMatchHistory::getUserId, userId)
                        .orderByDesc(UserMatchHistory::getCreatedAt)
                        .last("limit " + lim)
        );
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserMatchHistory m : list) {
            Map<String, Object> x = new HashMap<>();
            x.put("id", m.getId());
            x.put("targetRole", m.getTargetRole());
            x.put("city", m.getCity());
            x.put("createdAt", m.getCreatedAt());
            out.add(x);
        }
        return out;
    }

    private Long requireUserId() {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("未登录用户不可调用 user_* 工具");
        }
        return userId;
    }

    private Map<String, Object> mapFavorite(UserFavoriteJob j) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", j.getId());
        m.put("sourceTable", j.getSourceTable());
        m.put("jobId", j.getJobId());
        m.put("jobUrl", j.getJobUrl());
        m.put("jobName", j.getJobName());
        m.put("companyName", j.getCompanyName());
        m.put("city", j.getCity());
        m.put("salaryMin", j.getSalaryMin());
        m.put("salaryMax", j.getSalaryMax());
        m.put("experience", j.getExperience());
        m.put("education", j.getEducation());
        m.put("createdAt", j.getCreatedAt());
        m.put("job", readJsonAsObject(j.getJobJson()));
        return m;
    }

    private Map<String, Object> mapHistory(UserJobHistory j) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", j.getId());
        m.put("sourceTable", j.getSourceTable());
        m.put("jobUrl", j.getJobUrl());
        m.put("jobName", j.getJobName());
        m.put("companyName", j.getCompanyName());
        m.put("city", j.getCity());
        m.put("salaryMin", j.getSalaryMin());
        m.put("salaryMax", j.getSalaryMax());
        m.put("experience", j.getExperience());
        m.put("education", j.getEducation());
        m.put("createdAt", j.getCreatedAt());
        m.put("updatedAt", j.getUpdatedAt());
        m.put("job", readJsonAsObject(j.getJobJson()));
        return m;
    }

    private String writeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> readJsonAsMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Object readJsonAsObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}

