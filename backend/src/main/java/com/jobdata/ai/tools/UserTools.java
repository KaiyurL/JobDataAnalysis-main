package com.jobdata.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.context.UserContextHolder;
import com.jobdata.entity.UserFavoriteJob;
import com.jobdata.entity.UserJobHistory;
import com.jobdata.entity.UserProfile;
import com.jobdata.service.UserFavoriteJobService;
import com.jobdata.service.UserJobHistoryService;
import com.jobdata.service.UserProfileService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户数据工具类，提供大模型可调用的用户相关工具
 */
@Component
public class UserTools {

    private final UserProfileService userProfileService;
    private final UserFavoriteJobService userFavoriteJobService;
    private final UserJobHistoryService userJobHistoryService;
    private final ObjectMapper objectMapper;

    public UserTools(
            UserProfileService userProfileService,
            UserFavoriteJobService userFavoriteJobService,
            UserJobHistoryService userJobHistoryService,
            ObjectMapper objectMapper
    ) {
        this.userProfileService = userProfileService;
        this.userFavoriteJobService = userFavoriteJobService;
        this.userJobHistoryService = userJobHistoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取当前登录用户的画像
     *
     * @return 包含 profile、resumeMeta、profileExtra 的用户画像数据
     */
    @Tool(name = "user_get_profile", description = "读取当前登录用户的画像（包含 profile/resumeMeta/profileExtra）。当用户询问我的画像是什么/我之前填了什么/按我的情况推荐时使用。")
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

    /**
     * 更新当前登录用户画像
     *
     * @param profile 画像对象
     * @param resumeMeta 简历元信息
     * @param profileExtra 额外信息
     * @return 更新后的用户画像
     */
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

    /**
     * 列出当前用户收藏的岗位
     *
     * @param limit 返回数量上限，默认 20
     * @return 收藏岗位列表
     */
    @Tool(name = "user_list_favorites", description = "列出当前用户收藏的岗位。用户问我收藏了哪些/基于收藏推荐时使用。")
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

    /**
     * 列出当前用户浏览过的岗位历史
     *
     * @param limit 返回数量上限，默认 50
     * @return 浏览历史列表
     */
    @Tool(name = "user_list_job_history", description = "列出当前用户浏览/查看过的岗位历史。用户问我最近看过什么/基于历史推荐时使用。")
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

    /**
     * 获取当前用户ID，未登录时抛出异常
     */
    private Long requireUserId() {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("未登录用户不可调用 user_* 工具");
        }
        return userId;
    }

    /**
     * 将收藏记录转换为 Map
     */
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

    /**
     * 将浏览历史转换为 Map
     */
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

    /**
     * 将对象序列化为 JSON 字符串
     */
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

    /**
     * 将 JSON 字符串反序列化为 Map
     */
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

    /**
     * 将 JSON 字符串反序列化为对象
     */
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
