package com.jobdata.dto;

import lombok.Data;

/**
 * 公司薪资统计 DTO：包含公司名称与平均薪资。
 */
@Data
public class CompanySalaryDTO {
    private String companyName;
    private Double avgSalary;
}
