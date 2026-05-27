package com.jobdata.dto;

import lombok.Data;

/**
 * 公司规模统计 DTO：包含规模区间与数量。
 */
@Data
public class CompanySizeDTO {
    private String size;
    private Integer count;
}
