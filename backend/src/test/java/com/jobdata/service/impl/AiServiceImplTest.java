package com.jobdata.service.impl;

import com.jobdata.ai.AgentChatResponse;
import com.jobdata.ai.AgentChatService;
import com.jobdata.dto.AiChatRequest;
import com.jobdata.dto.AiChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    @Mock
    AgentChatService agentChatService;

    @Test
    void careerChat_delegates_to_agent() {
        AgentChatResponse agentResp = new AgentChatResponse();
        agentResp.setReply("ok");
        when(agentChatService.chatOnce(any(AiChatRequest.class), eq(1L))).thenReturn(agentResp);

        AiServiceImpl svc = new AiServiceImpl(agentChatService);
        AiChatResponse out = svc.careerChat(new AiChatRequest(), 1L);
        assertEquals("ok", out.getReply());
    }
}

