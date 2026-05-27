package com.jobdata.dto;

import lombok.Data;

/**
 * 学历薪资统计 DTO：包含学历、平均薪资与样本数量。
 */
@Data
public class EducationSalaryDTO {
    private String education;
    private Double avgSalary;
    private Integer count;
}
