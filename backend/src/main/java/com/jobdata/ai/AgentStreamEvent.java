package com.jobdata.ai;

import java.util.Map;

public class AgentStreamEvent {
    private String type;
    private String text;
    private Map<String, Object> payload;

    public AgentStreamEvent() {
    }

    public AgentStreamEvent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public AgentStreamEvent(String type, String text, Map<String, Object> payload) {
        this.type = type;
        this.text = text;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}

