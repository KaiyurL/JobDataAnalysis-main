package com.jobdata.dto;

import lombok.Data;

/**
 * 热门公司统计 DTO：包含公司名称与职位数量。
 */
@Data
public class CompanyHotDTO {
    private String companyName;
    private Integer count;
}
