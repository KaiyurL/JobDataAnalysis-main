package com.jobdata.ai.dto;

import java.util.Map;

/**
 * SSE 流式对话事件：start/delta/end。
 */
public class AgentStreamEvent {
    private String type;
    private String text;
    private Map<String, Object> payload;

    /**
     * 创建空事件。
     */
    public AgentStreamEvent() {
    }

    /**
     * 创建事件（无 payload）。
     *
     * @param type 事件类型（start/delta/end）
     * @param text 文本内容
     */
    public AgentStreamEvent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    /**
     * 创建事件（带 payload）。
     *
     * @param type 事件类型（start/delta/end）
     * @param text 文本内容
     * @param payload 附加数据
     */
    public AgentStreamEvent(String type, String text, Map<String, Object> payload) {
        this.type = type;
        this.text = text;
        this.payload = payload;
    }

    /**
     * 获取事件类型。
     */
    public String getType() {
        return type;
    }

    /**
     * 设置事件类型。
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取事件文本。
     */
    public String getText() {
        return text;
    }

    /**
     * 设置事件文本。
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 获取事件附加数据。
     */
    public Map<String, Object> getPayload() {
        return payload;
    }

    /**
     * 设置事件附加数据。
     */
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}

