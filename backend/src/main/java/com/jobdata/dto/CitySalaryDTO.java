package com.jobdata.dto;

import lombok.Data;

@Data
public class CitySalaryDTO {
    private String city;
    private Double avgSalary;
    private Integer count;
}
