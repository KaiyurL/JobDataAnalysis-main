package com.jobdata.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class JobMatchResponse {
    private Long id;
    private String jobName;
    private String companyName;
    private Integer salaryMin;
    private Integer salaryMax;
    private BigDecimal salaryAvg;
    private String city;
    private Double matchScore;
}
