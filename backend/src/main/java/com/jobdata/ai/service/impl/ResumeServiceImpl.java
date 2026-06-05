package com.jobdata.ai.service.impl;

import com.jobdata.ai.service.ResumeService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历解析服务实现：支持 PDF/DOC/DOCX/TXT 文本提取，并可调用百炼模型抽取结构化 Profile。
 */
@Service
public class ResumeServiceImpl implements ResumeService {

    @Value("${bailian.apiKey:${AI_DASHSCOPE_API_KEY:}}")
    private String apiKeyFromConfig;

    @Value("${bailian.baseUrl:https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions}")
    private String baseUrl;

    @Value("${bailian.model:qwen3.5-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 解析简历文件为纯文本：优先直接提取文字，必要时尝试 OCR。
     *
     * @param file 简历文件
     * @return 纯文本内容
     * @throws Exception 解析异常
     */
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
                    stripper.setSortByPosition(true);
                    stripper.setLineSeparator("\n");
                    String text = normalizeExtractedText(stripper.getText(document));
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                    String ocr = ocrPdfToText(document);
                    return normalizeExtractedText(ocr);
                }
            } else if (filename.endsWith(".docx")) {
                try (XWPFDocument document = new XWPFDocument(is)) {
                    String text;
                    try (XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                        text = extractor.getText();
                    }
                    text = normalizeExtractedText(text);
                    if (StringUtils.hasText(text)) {
                        return text;
                    }
                    String fallback = extractDocxFallback(document);
                    fallback = normalizeExtractedText(fallback);
                    if (StringUtils.hasText(fallback)) {
                        return fallback;
                    }
                    String xmlText = extractDocxXmlText(document);
                    xmlText = normalizeExtractedText(xmlText);
                    if (StringUtils.hasText(xmlText)) {
                        return xmlText;
                    }
                    String ocr = ocrDocxToText(document);
                    return normalizeExtractedText(ocr);
                }
            } else if (filename.endsWith(".doc")) {
                try (HWPFDocument document = new HWPFDocument(is);
                     WordExtractor extractor = new WordExtractor(document)) {
                    return normalizeExtractedText(extractor.getText());
                }
            } else if (filename.endsWith(".txt")) {
                byte[] bytes = file.getBytes();
                String t = new String(bytes, StandardCharsets.UTF_8);
                if (t.contains("\uFFFD")) {
                    t = new String(bytes, Charset.forName("GBK"));
                }
                return normalizeExtractedText(t);
            } else {
                throw new IllegalArgumentException("不支持的文件格式，仅支持 PDF, DOCX, DOC, TXT");
            }
        }
    }

    private static class ImagePayload {
        private final String mime;
        private final String base64;

        private ImagePayload(String mime, String base64) {
            this.mime = mime;
            this.base64 = base64;
        }
    }

    private String ocrPdfToText(PDDocument document) {
        List<ImagePayload> images = new ArrayList<>();
        PDFRenderer renderer = new PDFRenderer(document);
        int pages = Math.min(document.getNumberOfPages(), 2);
        for (int i = 0; i < pages; i++) {
            try {
                BufferedImage img = renderer.renderImageWithDPI(i, 160, ImageType.RGB);
                String b64 = toPngBase64(img, 900);
                if (StringUtils.hasText(b64)) {
                    images.add(new ImagePayload("image/png", b64));
                }
                if (images.size() >= 2) {
                    break;
                }
            } catch (Exception e) {
            }
        }
        if (images.isEmpty()) {
            return "";
        }
        try {
            return ocrImagesWithBailian(images);
        } catch (Exception e) {
            throw new RuntimeException("OCR识别失败: " + e.getMessage(), e);
        }
    }

    private String ocrDocxToText(XWPFDocument document) {
        List<ImagePayload> images = new ArrayList<>();
        List<XWPFPictureData> pics = document.getAllPictures();
        if (pics != null) {
            for (XWPFPictureData p : pics) {
                if (p == null) {
                    continue;
                }
                byte[] bytes = p.getData();
                if (bytes == null || bytes.length == 0) {
                    continue;
                }
                String mime = p.getPackagePart() == null ? "" : String.valueOf(p.getPackagePart().getContentType());
                if (!StringUtils.hasText(mime) || !mime.startsWith("image/")) {
                    mime = "image/png";
                }

                String b64 = "";
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img != null) {
                        b64 = toPngBase64(img, 900);
                        mime = "image/png";
                    }
                } catch (Exception ignored) {
                }

                if (!StringUtils.hasText(b64)) {
                    if (bytes.length <= 900_000) {
                        b64 = Base64.getEncoder().encodeToString(bytes);
                    }
                }

                if (StringUtils.hasText(b64)) {
                    images.add(new ImagePayload(mime, b64));
                }
                if (images.size() >= 2) {
                    break;
                }
            }
        }
        if (images.isEmpty()) {
            return "";
        }
        try {
            return ocrImagesWithBailian(images);
        } catch (Exception e) {
            throw new RuntimeException("OCR识别失败: " + e.getMessage(), e);
        }
    }

    private String extractDocxXmlText(XWPFDocument document) {
        try {
            if (document == null || document.getDocument() == null) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            XmlCursor cursor = document.getDocument().newCursor();
            try {
                cursor.selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' .//w:t");
                while (cursor.toNextSelection()) {
                    String t = cursor.getTextValue();
                    if (StringUtils.hasText(t)) {
                        sb.append(t).append("\n");
                    }
                }
            } finally {
                cursor.dispose();
            }
            String out = sb.toString();
            if (out.contains("<w:") || out.contains("</w:") || out.contains("<w")) {
                out = out.replaceAll("<[^>]+>", " ");
            }
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    private String toPngBase64(BufferedImage img, int maxWidth) {
        try {
            BufferedImage out = img;
            if (img != null && maxWidth > 0 && img.getWidth() > maxWidth) {
                int w = maxWidth;
                int h = Math.max(1, (int) Math.round((double) img.getHeight() * w / img.getWidth()));
                BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                scaled.getGraphics().drawImage(img, 0, 0, w, h, null);
                out = scaled;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private String ocrImagesWithBailian(List<ImagePayload> images) throws Exception {
        String apiKey = StringUtils.hasText(apiKeyFromConfig) ? apiKeyFromConfig.trim() : "";
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        if (images == null || images.isEmpty()) {
            return "";
        }

        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "请对这些简历图片做 OCR，只输出识别出的纯文本内容，不要解释，不要 markdown，不要 JSON。");
        content.add(textPart);

        int max = Math.min(images.size(), 2);
        for (int i = 0; i < max; i++) {
            ImagePayload img = images.get(i);
            if (img == null || !StringUtils.hasText(img.base64)) {
                continue;
            }
            Map<String, Object> imgPart = new HashMap<>();
            imgPart.put("type", "image_url");
            Map<String, Object> imageUrl = new HashMap<>();
            String mime = StringUtils.hasText(img.mime) ? img.mime : "image/png";
            imageUrl.put("url", "data:" + mime + ";base64," + img.base64);
            imgPart.put("image_url", imageUrl);
            content.add(imgPart);
        }

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", content);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMsg);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.0);
        payload.put("max_tokens", 1024);

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
                    Object contentObj = msg.get("content");
                    if (contentObj != null) {
                        return String.valueOf(contentObj).trim();
                    }
                }
            }
        }
        return "";
    }

    private String extractDocxFallback(XWPFDocument document) {
        StringBuilder sb = new StringBuilder();
        appendParagraphs(sb, document.getParagraphs());
        appendTables(sb, document.getTables());

        List<XWPFHeader> headers = document.getHeaderList();
        if (headers != null) {
            for (XWPFHeader h : headers) {
                appendParagraphs(sb, h.getParagraphs());
                appendTables(sb, h.getTables());
            }
        }

        List<XWPFFooter> footers = document.getFooterList();
        if (footers != null) {
            for (XWPFFooter f : footers) {
                appendParagraphs(sb, f.getParagraphs());
                appendTables(sb, f.getTables());
            }
        }

        return sb.toString();
    }

    private void appendParagraphs(StringBuilder sb, List<XWPFParagraph> paragraphs) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return;
        }
        for (XWPFParagraph p : paragraphs) {
            if (p == null) {
                continue;
            }
            String t = p.getText();
            if (StringUtils.hasText(t)) {
                sb.append(t).append("\n");
            }
        }
    }

    private void appendTables(StringBuilder sb, List<XWPFTable> tables) {
        if (tables == null || tables.isEmpty()) {
            return;
        }
        for (XWPFTable t : tables) {
            if (t == null) {
                continue;
            }
            List<XWPFTableRow> rows = t.getRows();
            if (rows == null) {
                continue;
            }
            for (XWPFTableRow row : rows) {
                if (row == null) {
                    continue;
                }
                List<XWPFTableCell> cells = row.getTableCells();
                if (cells == null) {
                    continue;
                }
                for (XWPFTableCell cell : cells) {
                    if (cell == null) {
                        continue;
                    }
                    appendParagraphs(sb, cell.getParagraphs());
                }
            }
        }
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace("\u0000", "");
        t = t.replace("\r\n", "\n").replace("\r", "\n");
        t = t.replaceAll("[\\u00A0\\t\\f\\v]+", " ");
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    /**
     * 将简历文本提交给百炼模型，抽取结构化 Profile（JSON 字符串）。
     *
     * @param text 简历纯文本
     * @return 结构化 Profile（JSON）
     * @throws Exception 调用异常
     */
    @Override
    public String extractProfileFromText(String text) throws Exception {
        String apiKey = StringUtils.hasText(apiKeyFromConfig) ? apiKeyFromConfig.trim() : "";
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置百炼 API Key");
        }

        String prompt = "你是一个专业的简历分析助手。请从以下简历文本中提取结构化信息，并严格以 JSON 输出（只输出 JSON，不要有任何解释、不要 markdown、不要 ``` 包裹）。\n" +
                "字段要求（尽量填充，缺失就输出空字符串或空数组）：\n" +
                "1) targetRole (String): 目标岗位或当前岗位\n" +
                "2) city (String): 意向城市或当前所在城市\n" +
                "3) education (String): 最高学历，例如：大专、本科、硕士、博士\n" +
                "4) experience (String): 工作经验，例如：应届生、1-3年、3-5年、5-10年、10年以上\n" +
                "5) skills (String): 核心技能，用逗号分隔，例如：Java, Spring Boot, MySQL\n" +
                "6) notes (String): 简短总结（亮点/短板/建议补充）\n" +
                "7) highlights (Array<String>): 3-8 条亮点要点\n" +
                "8) workExperiences (Array<Object>): 每项包含 company,title,start,end,highlights(Array<String>),tech(Array<String>)\n" +
                "9) projects (Array<Object>): 每项包含 name,role,tech(Array<String>),highlights(Array<String>)\n" +
                "10) certifications (Array<String>): 证书/奖项\n" +
                "11) links (Array<String>): 个人链接（Github/博客等）\n\n" +
                "简历文本：\n" + text;

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "user");
        sysMsg.put("content", prompt);
        messages.add(sysMsg);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.1);

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

