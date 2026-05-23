package com.jobdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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
