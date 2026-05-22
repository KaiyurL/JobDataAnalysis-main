package com.jobdata.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.dto.Result;
import com.jobdata.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/parse")
    public Result<Map<String, Object>> parseResume(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        try {
            // 1. 解析文件到纯文本
            String text = resumeService.parseResumeFileToText(file);
            
            if (text == null || text.trim().isEmpty()) {
                return Result.error("未能从文件中提取到有效文本");
            }
            
            // 2. 将纯文本丢给大模型，提取结构化 JSON
            String jsonProfile = resumeService.extractProfileFromText(text);
            
            // 3. 验证并解析 JSON
            Map<String, Object> profileMap = objectMapper.readValue(jsonProfile, new TypeReference<Map<String, Object>>() {});
            return Result.success(profileMap);
        } catch (Exception e) {
            return Result.error("解析简历失败: " + e.getMessage());
        }
    }
}
