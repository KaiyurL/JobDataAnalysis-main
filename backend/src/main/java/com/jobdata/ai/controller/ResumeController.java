package com.jobdata.ai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.service.ResumeService;
import com.jobdata.dto.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 简历解析接口：上传简历文件，解析为文本并调用模型抽取结构化信息。
 */
@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析简历文件并返回结构化信息（JSON Map）。
     *
     * @param file 简历文件（支持 pdf/doc/docx/txt）
     * @return 结构化简历信息
     */
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

            String jsonProfile = resumeService.extractProfileFromText(clipped);

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

    /**
     * 截断文本，用于控制输入长度/输出预览长度。
     *
     * @param s 原始字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
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
