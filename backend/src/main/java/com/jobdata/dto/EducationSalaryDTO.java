package com.jobdata.dto;

import lombok.Data;

@Data
public class EducationSalaryDTO {
    private String education;
    private Double avgSalary;
    private Integer count;
}
