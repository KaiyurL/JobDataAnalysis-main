package com.jobdata.dto;

import lombok.Data;

/**
 * 关键词统计 DTO：包含关键词与出现次数。
 */
@Data
public class KeywordDTO {
    private String keyword;
    private Integer count;
}
