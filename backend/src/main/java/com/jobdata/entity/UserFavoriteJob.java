package com.jobdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorite_job")
public class UserFavoriteJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sourceTable;
    private Long jobId;
    private String jobUrl;
    private String jobName;
    private String companyName;
    private String city;
    private Integer salaryMin;
    private Integer salaryMax;
    private String experience;
    private String education;
    private String jobJson;
    private LocalDateTime createdAt;
}
