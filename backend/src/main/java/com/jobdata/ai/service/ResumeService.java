package com.jobdata.ai.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 简历解析服务：负责从文件提取文本，并调用模型抽取结构化 Profile。
 */
public interface ResumeService {
    /**
     * 上传并解析简历文件为文本。
     *
     * @param file 简历文件
     * @return 提取到的纯文本
     * @throws Exception 解析异常
     */
    String parseResumeFileToText(MultipartFile file) throws Exception;

    /**
     * 将简历文本提交给大模型，提取结构化 Profile。
     *
     * @param text 简历纯文本
     * @return 结构化 Profile（JSON 字符串）
     * @throws Exception 调用/解析异常
     */
    String extractProfileFromText(String text) throws Exception;
}

