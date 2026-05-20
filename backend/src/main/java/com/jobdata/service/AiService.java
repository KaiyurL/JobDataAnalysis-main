package com.jobdata.service;

import com.jobdata.dto.AiChatRequest;
import com.jobdata.dto.AiChatResponse;

public interface AiService {
    AiChatResponse careerChat(AiChatRequest request);
}

