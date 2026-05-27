package com.jobdata.dto;

import lombok.Data;

/**
 * 城市薪资统计 DTO：包含城市、平均薪资与样本数量。
 */
@Data
public class CitySalaryDTO {
    private String city;
    private Double avgSalary;
    private Integer count;
}
