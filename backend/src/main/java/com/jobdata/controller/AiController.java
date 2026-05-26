package com.jobdata.controller;

import com.jobdata.dto.AiChatRequest;
import com.jobdata.dto.AiChatResponse;
import com.jobdata.dto.Result;
import com.jobdata.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    @Autowired
    private AiService aiService;

    @PostMapping("/career-chat")
    public Result<AiChatResponse> careerChat(Authentication authentication, @RequestBody AiChatRequest request) {
        try {
            Long userId = authentication != null && authentication.getPrincipal() instanceof Long ? (Long) authentication.getPrincipal() : null;
            return Result.success(aiService.careerChat(request, userId));
        } catch (Exception e) {
            log.error("AI career-chat failed: {}", e.getMessage(), e);
            return Result.error("AI 建议生成失败: " + e.getMessage());
        }
    }
}
