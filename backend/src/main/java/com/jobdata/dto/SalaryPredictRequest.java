package com.jobdata.dto;

import lombok.Data;

@Data
public class SalaryPredictRequest {
    private String education;
    private String experience;
    private String city;
    private String keyword;
}
