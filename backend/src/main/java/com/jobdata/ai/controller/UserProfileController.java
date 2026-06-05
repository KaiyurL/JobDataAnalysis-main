package com.jobdata.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.entity.UserProfile;
import com.jobdata.ai.service.UserProfileService;
import com.jobdata.dto.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户画像接口：管理当前登录用户的画像信息与简历元数据等结构化内容。
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

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
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }

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
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        if (userId == null) {
            return Result.fail("未登录");
        }

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
}
