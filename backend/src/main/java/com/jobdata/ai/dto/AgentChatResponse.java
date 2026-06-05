package com.jobdata.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 对话响应对象：包含最终文本回复与最终推荐岗位卡片列表。
 */
public class AgentChatResponse {
    private String reply;
    private List<Map<String, Object>> jobCards;

    /**
     * 获取最终回复文本。
     *
     * @return 回复文本
     */
    public String getReply() {
        return reply;
    }

    /**
     * 设置最终回复文本。
     *
     * @param reply 回复文本
     */
    public void setReply(String reply) {
        this.reply = reply;
    }

    /**
     * 获取推荐岗位卡片列表。
     *
     * @return 岗位卡片列表
     */
    public List<Map<String, Object>> getJobCards() {
        return jobCards;
    }

    /**
     * 设置推荐岗位卡片列表。
     *
     * @param jobCards 岗位卡片列表
     */
    public void setJobCards(List<Map<String, Object>> jobCards) {
        this.jobCards = jobCards;
    }
}

