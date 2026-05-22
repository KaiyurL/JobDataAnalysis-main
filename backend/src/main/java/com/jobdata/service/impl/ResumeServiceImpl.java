package com.jobdata.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.service.ResumeService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResumeServiceImpl implements ResumeService {

    @Value("${bailian.apiKey:${AI_DASHSCOPE_API_KEY:}}")
    private String apiKeyFromConfig;

    @Value("${bailian.baseUrl:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String baseUrl;

    @Value("${bailian.model:qwen3.5-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String parseResumeFileToText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名为空");
        }
        filename = filename.toLowerCase();
        
        try (InputStream is = file.getInputStream()) {
            if (filename.endsWith(".pdf")) {
                try (PDDocument document = PDDocument.load(is)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                }
            } else if (filename.endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(is);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            } else if (filename.endsWith(".doc")) {
                try (HWPFDocument document = new HWPFDocument(is);
                     WordExtractor extractor = new WordExtractor(document)) {
                    return extractor.getText();
                }
            } else if (filename.endsWith(".txt")) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            } else {
                throw new IllegalArgumentException("不支持的文件格式，仅支持 PDF, DOCX, DOC, TXT");
            }
        }
    }

    @Override
    public String extractProfileFromText(String text) throws Exception {
        String apiKey = StringUtils.hasText(apiKeyFromConfig) ? apiKeyFromConfig.trim() : "";
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置百炼 API Key");
        }

        String prompt = "你是一个专业的简历分析助手。请从以下简历文本中提取出结构化信息，并严格以 JSON 格式输出，不要有任何其他多余的话语或 markdown 标记(不要用```json包裹)。\n" +
                "需要提取的字段如下：\n" +
                "- targetRole (String): 目标岗位或当前岗位\n" +
                "- city (String): 意向城市或当前所在城市\n" +
                "- education (String): 最高学历，例如：大专、本科、硕士、博士\n" +
                "- experience (String): 工作经验，例如：应届生、1-3年、3-5年、5-10年、10年以上\n" +
                "- skills (String): 掌握的核心技能，用逗号分隔，例如：Java, Spring Boot, MySQL\n" +
                "- notes (String): 提取简历中的一些亮点或待补充点，例如学校、大厂经历或缺乏的项目经验等\n\n" +
                "简历文本：\n" + text;

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "user");
        sysMsg.put("content", prompt);
        messages.add(sysMsg);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.1); // 降低随机性，确保输出结构化

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        Map<String, Object> response = restTemplate.postForObject(baseUrl, entity, Map.class);

        if (response != null && response.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                if (msg != null) {
                    String content = (String) msg.get("content");
                    if (content != null) {
                        content = content.trim();
                        // 移除可能存在的 markdown json 标记
                        if (content.startsWith("```json")) {
                            content = content.substring(7);
                        } else if (content.startsWith("```")) {
                            content = content.substring(3);
                        }
                        if (content.endsWith("```")) {
                            content = content.substring(0, content.length() - 3);
                        }
                        return content.trim();
                    }
                }
            }
        }
        throw new RuntimeException("AI 解析简历失败或返回格式错误");
    }
}
