package com.jobdata.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 对话请求：包含用户画像、当前问题与历史对话上下文。
 */
@Data
public class AiChatRequest {
    private Map<String, Object> profile;
    private String message;
    private List<Map<String, String>> history;
}

