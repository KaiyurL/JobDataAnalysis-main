package com.jobdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("job_info")
public class JobInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;

    private String companyName;

    private String city;

    private Integer salaryMin;

    private Integer salaryMax;

    private BigDecimal salaryAvg;

    private String experience;

    private String education;

    private String jobDesc;

    private String jobKeywords;

    private String companySize;

    private String companyIndustry;

    private LocalDate publishDate;

    private LocalDateTime createdAt;

}
