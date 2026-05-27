package com.jobdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户画像实体：对应 user_profile 表。
 */
@Data
@TableName("user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String profileJson;
    private String resumeMetaJson;
    private String profileExtraJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
