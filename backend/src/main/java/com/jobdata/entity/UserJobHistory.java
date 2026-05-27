package com.jobdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户浏览历史实体：对应 user_job_history 表。
 */
@Data
@TableName("user_job_history")
public class UserJobHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String sourceTable;
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
    private LocalDateTime updatedAt;
}
