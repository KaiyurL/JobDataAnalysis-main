package com.jobdata.service;

import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {
    /**
     * 上传并解析简历文件为文本
     */
    String parseResumeFileToText(MultipartFile file) throws Exception;

    /**
     * 将简历文本提交给大模型，提取结构化 Profile
     */
    String extractProfileFromText(String text) throws Exception;
}
