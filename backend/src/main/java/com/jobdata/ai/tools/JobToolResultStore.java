package com.jobdata.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具结果存储：用于在一次请求/一次对话中暂存 job_search 生成的岗位卡片列表。
 */
@Component
public class JobToolResultStore {
    private final ThreadLocal<List<Map<String, Object>>> lastJobCards = new ThreadLocal<>();

    /**
     * 设置本次对话的岗位卡片列表。
     *
     * @param cards 岗位卡片列表
     */
    public void setLastJobCards(List<Map<String, Object>> cards) {
        lastJobCards.set(cards);
    }

    /**
     * 获取并清空本次对话的岗位卡片列表。
     *
     * @return 岗位卡片列表（可能为 null）
     */
    public List<Map<String, Object>> consumeLastJobCards() {
        List<Map<String, Object>> cards = lastJobCards.get();
        lastJobCards.remove();
        return cards;
    }
}
