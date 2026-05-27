package com.jobdata.ai.controller;

import com.jobdata.ai.model.AgentChatResponse;
import com.jobdata.ai.model.AgentStreamEvent;
import com.jobdata.ai.service.AgentChatService;
import com.jobdata.dto.AiChatRequest;
import com.jobdata.dto.Result;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI Agent 控制器，提供聊天接口。
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentChatService agentChatService;

    public AgentController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(Authentication authentication, @RequestBody AiChatRequest request) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        return Result.success(agentChatService.chatOnce(request, userId));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentStreamEvent>> chatStream(Authentication authentication, @RequestBody AiChatRequest request) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
        return agentChatService.chatStream(request, userId);
    }
}
