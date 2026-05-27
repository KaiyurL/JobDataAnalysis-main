package com.jobdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户匹配历史实体：对应 user_match_history 表。
 */
@Data
@TableName("user_match_history")
public class UserMatchHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String targetRole;
    private String city;
    private String profileJson;
    private String resultJson;
    private LocalDateTime createdAt;
}
