package com.jobdata.dto;

import lombok.Data;

@Data
public class JobSearchRequest {
    private String source;
    private String keyword;
    private String city;
    private String education;
    private String experience;
    private Integer minSalaryK;
    private Integer maxSalaryK;
    private String company;
    private Integer limit;
}
