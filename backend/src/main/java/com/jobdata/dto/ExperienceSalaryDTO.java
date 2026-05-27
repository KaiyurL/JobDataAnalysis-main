package com.jobdata.dto;

import lombok.Data;

/**
 * 经验薪资统计 DTO：包含经验、平均薪资与样本数量。
 */
@Data
public class ExperienceSalaryDTO {
    private String experience;
    private Double avgSalary;
    private Integer count;
}
