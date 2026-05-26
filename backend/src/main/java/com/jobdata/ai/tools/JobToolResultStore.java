package com.jobdata.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JobToolResultStore {
    private final ThreadLocal<List<Map<String, Object>>> lastJobCards = new ThreadLocal<>();

    public void setLastJobCards(List<Map<String, Object>> cards) {
        lastJobCards.set(cards);
    }

    public List<Map<String, Object>> consumeLastJobCards() {
        List<Map<String, Object>> cards = lastJobCards.get();
        lastJobCards.remove();
        return cards;
    }
}

