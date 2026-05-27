package com.jobdata.dto;

import lombok.Data;

/**
 * 行业统计 DTO：包含行业名称与数量。
 */
@Data
public class IndustryCountDTO {
    private String industry;
    private Integer count;
}
