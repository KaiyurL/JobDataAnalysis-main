package com.jobdata.service.impl;

import com.jobdata.ai.AgentChatResponse;
import com.jobdata.ai.AgentChatService;
import com.jobdata.dto.AiChatRequest;
import com.jobdata.dto.AiChatResponse;
import com.jobdata.service.AiService;
import org.springframework.stereotype.Service;

@Service
public class AiServiceImpl implements AiService {
    private final AgentChatService agentChatService;

    public AiServiceImpl(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @Override
    public AiChatResponse careerChat(AiChatRequest request, Long userId) {
        AgentChatResponse resp = agentChatService.chatOnce(request, userId);
        AiChatResponse out = new AiChatResponse();
        out.setReply(resp == null ? "" : resp.getReply());
        out.setRequestId(null);
        return out;
    }
}
