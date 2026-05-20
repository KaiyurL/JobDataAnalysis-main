package com.jobdata.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiChatRequest {
    private Map<String, Object> profile;
    private String message;
    private List<Map<String, String>> history;
}

