package com.jobdata.ai.model;

import java.util.List;
import java.util.Map;

/**
 * AI 对话响应对象：包含最终文本回复与最终推荐岗位卡片列表。
 */
public class AgentChatResponse {
    private String reply;
    private List<Map<String, Object>> jobCards;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<Map<String, Object>> getJobCards() {
        return jobCards;
    }

    public void setJobCards(List<Map<String, Object>> jobCards) {
        this.jobCards = jobCards;
    }
}
