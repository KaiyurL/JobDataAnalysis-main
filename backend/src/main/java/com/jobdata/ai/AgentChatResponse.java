package com.jobdata.ai;

import java.util.List;
import java.util.Map;

public class AgentChatResponse {
    private String reply;
    private List<Map<String, Object>> jobCards;
    private List<Map<String, Object>> citations;

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

    public List<Map<String, Object>> getCitations() {
        return citations;
    }

    public void setCitations(List<Map<String, Object>> citations) {
        this.citations = citations;
    }
}

