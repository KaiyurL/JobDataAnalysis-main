package com.jobdata.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.dto.Result;
import com.jobdata.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
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
            String filename = file.getOriginalFilename() == null ? "" : String.valueOf(file.getOriginalFilename());
            String lower = filename.toLowerCase();
            String fileType = lower.endsWith(".pdf") ? "pdf"
                    : lower.endsWith(".docx") ? "docx"
                    : lower.endsWith(".doc") ? "doc"
                    : lower.endsWith(".txt") ? "txt"
                    : "unknown";

            // 1. 解析文件到纯文本
            String text = resumeService.parseResumeFileToText(file);
            
            if (text == null || text.trim().isEmpty()) {
                if ("pdf".equals(fileType)) {
                    return Result.error("未能从 PDF 中提取到有效文本（可能是扫描件/图片型 PDF）。建议上传可复制文本的 PDF，或导出为 DOCX/TXT 再试。");
                }
                if ("docx".equals(fileType) || "doc".equals(fileType)) {
                    return Result.error("未能从 Word 文件中提取到有效文本（可能主要是图片/复杂排版）。建议另存为“纯文本 TXT”或复制粘贴文本后再试。");
                }
                return Result.error("未能从文件中提取到有效文本");
            }

            String normalized = text.replace("\u0000", "").trim();
            String clipped = clipText(normalized, 15000);
            
            // 2. 将纯文本丢给大模型，提取结构化 JSON
            String jsonProfile = resumeService.extractProfileFromText(clipped);
            
            // 3. 验证并解析 JSON
            Map<String, Object> profileMap = objectMapper.readValue(jsonProfile, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> meta = new HashMap<>();
            meta.put("fileName", filename);
            meta.put("fileType", fileType);
            meta.put("textLength", normalized.length());
            meta.put("textPreview", clipText(normalized, 2000));
            meta.put("rich", "pdf".equals(fileType) || "docx".equals(fileType));
            profileMap.put("_resume", meta);
            return Result.success(profileMap);
        } catch (Exception e) {
            return Result.error("解析简历失败: " + e.getMessage());
        }
    }

    private static String clipText(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (maxLen <= 0 || t.length() <= maxLen) {
            return t;
        }
        return t.substring(0, maxLen);
    }
}
