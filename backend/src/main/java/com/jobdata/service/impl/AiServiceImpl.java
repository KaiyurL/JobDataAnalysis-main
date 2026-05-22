package com.jobdata.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.dto.AiChatRequest;
import com.jobdata.dto.AiChatResponse;
import com.jobdata.service.AiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiServiceImpl implements AiService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${bailian.apiKey}")
    private String apiKeyFromConfig;

    @Value("${bailian.baseUrl}")
    private String baseUrl;

    @Value("${bailian.model}")
    private String model;

    @Value("${bailian.timeoutMs}")
    private Integer timeoutMs;

    @Override
    public AiChatResponse careerChat(AiChatRequest request) {
        String apiKey = resolveApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置百炼 API Key，请在 application.yml 配置 bailian.apiKey");
        }

        String system = buildSystemPrompt(request == null ? null : request.getProfile());
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(msg("system", system));

        if (request != null && request.getHistory() != null) {
            for (Map<String, String> h : request.getHistory()) {
                if (h == null) {
                    continue;
                }
                String role = safeStr(h.get("role"));
                String content = safeStr(h.get("content"));
                if (!StringUtils.hasText(role) || !StringUtils.hasText(content)) {
                    continue;
                }
                if (!("user".equals(role) || "assistant".equals(role))) {
                    continue;
                }
                messages.add(msg(role, content));
            }
        }

        if (request != null && StringUtils.hasText(request.getMessage())) {
            messages.add(msg("user", request.getMessage().trim()));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.7);
        payload.put("top_p", 0.9);
        payload.put("max_tokens", 1024);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        RestTemplate restTemplate = buildRestTemplate(timeoutMs);
        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
        } catch (RestClientResponseException ex) {
            String body = safeStr(ex.getResponseBodyAsString());
            String trimmed = body.length() > 800 ? body.substring(0, 800) : body;
            throw new IllegalStateException("百炼请求失败: HTTP " + ex.getRawStatusCode() + " - " + trimmed);
        }

        Map body = resp.getBody();
        String reply = extractReply(body);
        AiChatResponse out = new AiChatResponse();
        out.setReply(reply);
        out.setRequestId(extractRequestId(body));
        return out;
    }

    private RestTemplate buildRestTemplate(Integer timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int t = timeout == null ? 60000 : timeout;
        factory.setConnectTimeout(t);
        factory.setReadTimeout(t);
        return new RestTemplate(factory);
    }

    private String resolveApiKey() {
        if (StringUtils.hasText(apiKeyFromConfig)) {
            return apiKeyFromConfig.trim();
        }
        return "";
    }

    private Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private String safeStr(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String buildSystemPrompt(Map<String, Object> profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深求职顾问和AI面试官。你的任务是基于用户的画像与对话内容，提供精准的岗位匹配分析、差距分析、投递建议和面试辅导。");
        sb.append("输出要求：使用中文；态度专业友好；结构清晰；给出具体可执行的步骤；避免编造虚假信息。");

        String p = renderProfile(profile);
        if (StringUtils.hasText(p)) {
            sb.append("\n\n【用户当前画像】\n").append(p);
        }

        sb.append("\n\n请根据用户的具体提问，灵活提供以下部分或全部内容：\n");
        sb.append("1. **岗位匹配与差距分析**：对比用户的技能/经验与目标岗位的核心要求，指出匹配点和缺失点。\n");
        sb.append("2. **简历优化建议**：针对目标岗位，提供可以直接写到简历上的亮点描述（bullet points）。\n");
        sb.append("3. **面试实战辅导**：生成可能遇到的专业面试题及考察意图，并给出 STAR 原则的项目讲述结构。\n");
        sb.append("4. **投递与学习计划**：制定清晰的短期（如30天）技能提升和投递行动计划。\n");
        
        return sb.toString();
    }

    private String renderProfile(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return "";
        }
        List<String> keys = Arrays.asList("targetRole", "city", "education", "experience", "skills", "notes");
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            Object v = profile.get(k);
            String s = safeStr(v).trim();
            if (!StringUtils.hasText(s)) {
                continue;
            }
            sb.append(k).append(": ").append(s).append("\n");
        }
        String other = "";
        try {
            Map<String, Object> copy = new HashMap<>(profile);
            for (String k : keys) {
                copy.remove(k);
            }
            if (!copy.isEmpty()) {
                other = objectMapper.writeValueAsString(copy);
            }
        } catch (Exception ignored) {
        }
        if (StringUtils.hasText(other)) {
            sb.append("extra: ").append(other).append("\n");
        }
        return sb.toString().trim();
    }

    private String extractReply(Map body) {
        if (body == null) {
            return "";
        }
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List)) {
            return "";
        }
        List choices = (List) choicesObj;
        if (choices.isEmpty()) {
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map)) {
            return "";
        }
        Map firstMap = (Map) first;
        Object msgObj = firstMap.get("message");
        if (!(msgObj instanceof Map)) {
            return "";
        }
        Map msg = (Map) msgObj;
        Object content = msg.get("content");
        return safeStr(content).trim();
    }

    private String extractRequestId(Map body) {
        if (body == null) {
            return "";
        }
        Object id = body.get("id");
        String s = safeStr(id).trim();
        if (StringUtils.hasText(s)) {
            return s;
        }
        Object requestId = body.get("request_id");
        return safeStr(requestId).trim();
    }
}
