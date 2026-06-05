package com.jobdata.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.controller.AgentController;
import com.jobdata.ai.dto.AgentChatResponse;
import com.jobdata.ai.dto.AgentStreamEvent;
import com.jobdata.ai.service.AgentChatService;
import com.jobdata.dto.AiChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    MockMvc mockMvc;

    ObjectMapper objectMapper;

    @Mock
    AgentChatService agentChatService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentController(agentChatService)).build();
    }

    @Test
    void chat_returns_result_success() throws Exception {
        AgentChatResponse resp = new AgentChatResponse();
        resp.setReply("hello");
        resp.setJobCards(List.of(Map.of("source", "boss", "jobName", "Java开发")));
        when(agentChatService.chatOnce(any(AiChatRequest.class), any())).thenReturn(resp);

        AiChatRequest req = new AiChatRequest();
        req.setMessage("hi");

        mockMvc.perform(
                        post("/api/agent/chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reply").value("hello"));
    }

    @Test
    void chatStream_returns_sse() throws Exception {
        Flux<ServerSentEvent<AgentStreamEvent>> flux = Flux.just(
                ServerSentEvent.builder(new AgentStreamEvent("start", "")).build(),
                ServerSentEvent.builder(new AgentStreamEvent("delta", "hi")).build(),
                ServerSentEvent.builder(new AgentStreamEvent("end", "")).build()
        );
        when(agentChatService.chatStream(any(AiChatRequest.class), any())).thenReturn(flux);

        AiChatRequest req = new AiChatRequest();
        req.setMessage("hi");

        String body = mockMvc.perform(
                        post("/api/agent/chat/stream")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.TEXT_EVENT_STREAM)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(body.contains("data:"));
    }
}
