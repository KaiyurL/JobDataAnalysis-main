package com.jobdata.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.context.UserContextHolder;
import com.jobdata.ai.model.AgentChatResponse;
import com.jobdata.ai.model.AgentStreamEvent;
import com.jobdata.ai.tools.UserTools;
import com.jobdata.dto.AiChatRequest;
import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.service.JobInfo51JobService;
import com.jobdata.service.JobInfoService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AI Agent 聊天服务核心类，负责处理用户与智能助手的对话
 */
@Service
public class AgentChatService {

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(.*?)\\s*```");
    private static final Pattern MISSING_VALUE_COMMA = Pattern.compile(":\\s*,");
    private static final Pattern MISSING_VALUE_END_OBJ = Pattern.compile(":\\s*}");
    private static final int CANDIDATE_TOPK_MAX = 50;
    private static final Duration STREAM_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofMinutes(15);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final UserTools userTools;
    private final JobInfoService jobInfoService;
    private final JobInfo51JobService jobInfo51JobService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentChatService(ChatClient.Builder builder, VectorStore vectorStore, UserTools userTools, JobInfoService jobInfoService, JobInfo51JobService jobInfo51JobService) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.userTools = userTools;
        this.jobInfoService = jobInfoService;
        this.jobInfo51JobService = jobInfo51JobService;
    }

    
    /**
     * 执行一次性聊天，返回完整回复
     *
     * @param request 聊天请求，包含历史消息和当前消息
     * @param userId 用户ID
     * @return 聊天响应，包含回复文本与最终推荐岗位卡片
     */
    public AgentChatResponse chatOnce(AiChatRequest request, Long userId) {
        Long prevUserId = UserContextHolder.getUserId();
        UserContextHolder.setUserId(userId);
        try {
            String userMessage = buildUserMessage(request);
            Map<String, Object> effProfile = getEffectiveProfile(request, userId);
            List<Map<String, Object>> citations = buildCitations(userMessage, effProfile);
            List<Map<String, Object>> candidates = fetchCandidates(userMessage, effProfile);
            List<Map<String, Object>> recommended = selectRecommendations(userMessage, effProfile, citations, candidates, 8);
            String reply = generateFinalReply(userMessage, effProfile, citations, recommended);

            AgentChatResponse out = new AgentChatResponse();
            out.setReply(reply == null ? "" : reply);
            out.setJobCards(recommended);
            return out;
        } finally {
            if (prevUserId == null) {
                UserContextHolder.clear();
            } else {
                UserContextHolder.setUserId(prevUserId);
            }
        }
    }

    private Map<String, Object> getEffectiveProfile(AiChatRequest request, Long userId) {
        Map<String, Object> p = new HashMap<>();
        if (request != null && request.getProfile() != null) {
            p.putAll(request.getProfile());
        }
        if (userId != null) {
            try {
                Map<String, Object> dbWrap = userTools.userGetProfile();
                Object dbP = dbWrap.get("profile");
                if (dbP instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) dbP).entrySet()) {
                        p.putIfAbsent(String.valueOf(e.getKey()), e.getValue());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return p;
    }

    /**
     * 执行流式聊天，通过 SSE 逐步推送回复
     *
     * @param request 聊天请求
     * @param userId 用户ID
     * @return 流式事件序列
     */
    public Flux<ServerSentEvent<AgentStreamEvent>> chatStream(AiChatRequest request, Long userId) {
        Flux<ServerSentEvent<AgentStreamEvent>> start = Flux.just(ServerSentEvent.builder(new AgentStreamEvent("start", "")).build());

        Mono<PreparedStream> preparedMono = Mono.fromCallable(() -> prepareStream(request, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .cache();

        Flux<ServerSentEvent<AgentStreamEvent>> heartbeats = Flux.interval(STREAM_HEARTBEAT_INTERVAL)
                .map(i -> ServerSentEvent.<AgentStreamEvent>builder().comment("keep-alive").build())
                .takeUntilOther(preparedMono);

        Flux<ServerSentEvent<AgentStreamEvent>> reply = preparedMono
                .flatMapMany(prepared -> {
                    Flux<ServerSentEvent<AgentStreamEvent>> tokens = chatClient.prompt()
                            .system(prepared.system())
                            .user(prepared.user())
                            .stream()
                            .content()
                            .filter(t -> t != null && !t.isEmpty())
                            .map(t -> ServerSentEvent.builder(new AgentStreamEvent("delta", t)).build());

                    return tokens.concatWithValues(ServerSentEvent.builder(new AgentStreamEvent("end", "", payloadWithJobCards(prepared.jobCards()))).build())
                            .onErrorResume(e -> Flux.just(ServerSentEvent.builder(new AgentStreamEvent("end", "", payloadWithError(prepared.jobCards()))).build()));
                })
                .onErrorResume(e -> Flux.just(ServerSentEvent.builder(new AgentStreamEvent("end", "", payloadWithError(List.of()))).build()));

        return start.concatWith(heartbeats).concatWith(reply).timeout(STREAM_IDLE_TIMEOUT);
    }

    private record PreparedStream(String system, String user, List<Map<String, Object>> jobCards) {
    }

    private PreparedStream prepareStream(AiChatRequest request, Long userId) {
        Long prevUserId = UserContextHolder.getUserId();
        UserContextHolder.setUserId(userId);
        try {
            String userMessage = buildUserMessage(request);
            Map<String, Object> effProfile = getEffectiveProfile(request, userId);
            List<Map<String, Object>> citations = buildCitations(userMessage, effProfile);
            List<Map<String, Object>> candidates = fetchCandidates(userMessage, effProfile);
            List<Map<String, Object>> recommended = selectRecommendations(userMessage, effProfile, citations, candidates, 8);
            FinalReplyPrompt prompt = buildFinalReplyPrompt(userMessage, effProfile, citations, recommended);
            return new PreparedStream(prompt.system(), prompt.user(), recommended);
        } finally {
            if (prevUserId == null) {
                UserContextHolder.clear();
            } else {
                UserContextHolder.setUserId(prevUserId);
            }
        }
    }

    private Map<String, Object> payloadWithJobCards(List<Map<String, Object>> jobCards) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobCards", jobCards == null ? List.of() : jobCards);
        return payload;
    }

    private Map<String, Object> payloadWithError(List<Map<String, Object>> jobCards) {
        Map<String, Object> payload = payloadWithJobCards(jobCards);
        payload.put("error", "internal_error");
        return payload;
    }

    /**
     * 构建系统提示词，包含工具说明、用户画像和参考资料
     */
    private String buildSystemPrompt(AiChatRequest request, Long userId, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个招聘数据分析系统内置的智能体，负责基于真实岗位数据与用户画像、收藏岗位、浏览历史给出建议。");
        sb.append("要求：中文输出；结构清晰；不编造岗位；当需要岗位数据时应调用岗位检索工具；当需要用户数据时应调用 user_* 工具。");
        sb.append("\n\n【工具调用约束】");
        sb.append("\n- 工具参数必须是严格合法的 JSON。");
        sb.append("\n- 不允许出现空值占位（例如 \"maxSalaryK\": , 这种是非法 JSON）。");
        sb.append("\n- 不确定的字段请直接省略该字段，或显式写 null。");
        sb.append("\n- 数字字段只能是数字或 null，不要写空字符串。");
        sb.append("\n- city 支持逗号分隔多个城市。source 只允许 boss/51job/all。");
        sb.append("\n示例：{\"source\":\"all\",\"keyword\":\"后端\",\"city\":\"北京\",\"minSalaryK\":10,\"limit\":5}");
        sb.append("\n\n【策略】");
        sb.append("\n- 用户问“按我的情况/基于我的画像”时：优先使用用户画像（如果画像缺失则调用 user_get_profile），再结合岗位检索给建议。");
        sb.append("\n- 用户问“基于我的收藏/我收藏了哪些”时：先调用 user_list_favorites（如系统未提供收藏摘要）。");
        sb.append("\n- 用户问“我最近看过什么/基于浏览历史推荐”时：先调用 user_list_job_history（如系统未提供浏览摘要）。");
        sb.append("\n- 仅当用户明确要求“保存/更新画像”时才调用 user_upsert_profile。");
        sb.append("\n\n【可用工具】");
        sb.append("\n- 岗位检索工具：按条件检索岗位。");
        sb.append("\n- user_get_profile：读取当前用户画像。");
        sb.append("\n- user_list_favorites：读取收藏。");
        sb.append("\n- user_list_job_history：读取浏览历史。");
        sb.append("\n- user_upsert_profile：仅当用户明确要求“保存/修改画像”时才可调用。");

        if (userId != null) {
            sb.append("\n\n当前用户ID: ").append(userId);
        }

        Map<String, Object> profileMap = new HashMap<>();
        if (userId != null) {
            try {
                Map<String, Object> dbProfileWrap = userTools.userGetProfile();
                Object p = dbProfileWrap.get("profile");
                if (p instanceof Map) {
                    profileMap.putAll((Map<String, Object>) p);
                }
            } catch (Exception ignored) {
            }
        }
        if (request != null && request.getProfile() != null && !request.getProfile().isEmpty()) {
            profileMap.putAll(request.getProfile());
        }
        String profile = renderProfile(profileMap.isEmpty() ? null : profileMap);
        if (!profile.isEmpty()) {
            sb.append("\n\n【用户画像】\n").append(profile);
        }

        if (userId != null) {
            if (needFavorites(query)) {
                String fav = renderFavoritesForPromptSafe(10);
                if (!fav.isEmpty()) {
                    sb.append("\n\n【用户收藏（节选）】\n").append(fav);
                }
            }
            if (needJobHistory(query)) {
                String hist = renderJobHistoryForPromptSafe(10);
                if (!hist.isEmpty()) {
                    sb.append("\n\n【用户浏览历史（节选）】\n").append(hist);
                }
            }
        }

        List<Map<String, Object>> citations = buildCitations(query, profileMap);
        if (!citations.isEmpty()) {
            sb.append("\n\n【检索到的参考资料】\n");
            for (int i = 0; i < citations.size(); i++) {
                Map<String, Object> c = citations.get(i);
                sb.append(i + 1).append(". ").append(String.valueOf(c.getOrDefault("title", ""))).append("\n");
                Object snippet = c.get("snippet");
                if (snippet != null) {
                    sb.append(String.valueOf(snippet)).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 判断查询是否需要收藏信息
     */
    private boolean needFavorites(String query) {
        return containsAny(query, "收藏", "喜欢", "偏好");
    }

    /**
     * 判断查询是否需要浏览历史信息
     */
    private boolean needJobHistory(String query) {
        return containsAny(query, "浏览", "看过", "最近看", "历史记录");
    }

    /**
     * 判断文本是否包含任意关键词
     */
    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text.toLowerCase();
        for (String k : keywords) {
            if (k == null || k.isBlank()) {
                continue;
            }
            if (s.contains(k.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 安全地获取收藏列表并格式化
     */
    private String renderFavoritesForPromptSafe(int limit) {
        try {
            List<Map<String, Object>> list = userTools.userListFavorites(limit);
            return renderJobListForPrompt(list, limit);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 安全地获取浏览历史并格式化
     */
    private String renderJobHistoryForPromptSafe(int limit) {
        try {
            List<Map<String, Object>> list = userTools.userListJobHistory(limit);
            return renderJobListForPrompt(list, limit);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 将岗位列表格式化为提示词文本
     */
    private String renderJobListForPrompt(List<Map<String, Object>> list, int limit) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(limit, list.size());
        for (int i = 0; i < shown; i++) {
            Map<String, Object> m = list.get(i);
            if (m == null) {
                continue;
            }
            String jobName = asString(m.get("jobName"), "");
            String companyName = asString(m.get("companyName"), "");
            String city = asString(m.get("city"), "");
            String experience = asString(m.get("experience"), "");
            String education = asString(m.get("education"), "");
            String sourceTable = asString(m.get("sourceTable"), "");
            Integer salaryMin = asInteger(m.get("salaryMin"));
            Integer salaryMax = asInteger(m.get("salaryMax"));
            String salary = (salaryMin != null && salaryMax != null) ? (salaryMin + "-" + salaryMax + "K") : "面议";
            if (jobName.isEmpty() && companyName.isEmpty()) {
                continue;
            }
            sb.append(i + 1).append(". ")
                    .append(jobName.isEmpty() ? "（未命名岗位）" : jobName)
                    .append(companyName.isEmpty() ? "" : (" · " + companyName))
                    .append(city.isEmpty() ? "" : (" · " + city))
                    .append(" · ").append(salary)
                    .append(experience.isEmpty() ? "" : (" · " + experience))
                    .append(education.isEmpty() ? "" : (" · " + education))
                    .append(sourceTable.isEmpty() ? "" : (" · " + sourceTable))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 宽松解析 JSON 参数，容忍常见格式错误
     */
    private Map<String, Object> parseArgsJsonLenient(String raw) {
        if (raw == null) {
            return Map.of();
        }
        String s = raw.trim();
        var m = CODE_FENCE.matcher(s);
        if (m.matches()) {
            s = m.group(1).trim();
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a >= 0 && b > a) {
            s = s.substring(a, b + 1);
        }
        s = MISSING_VALUE_COMMA.matcher(s).replaceAll(": null,");
        s = MISSING_VALUE_END_OBJ.matcher(s).replaceAll(": null}");
        try {
            Map<String, Object> out = objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
            return out == null ? Map.of() : out;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * 安全地将对象转换为字符串
     */
    private String asString(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    /**
     * 安全地将对象转换为整数
     */
    private Integer asInteger(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Object metaFirst(Map<String, Object> meta, String... keys) {
        if (meta == null || meta.isEmpty() || keys == null || keys.length == 0) {
            return null;
        }
        for (String k : keys) {
            if (k == null || k.isBlank()) {
                continue;
            }
            if (meta.containsKey(k)) {
                Object v = meta.get(k);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private String metaString(Map<String, Object> meta, String def, String... keys) {
        Object v = metaFirst(meta, keys);
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private boolean isBlank(Object v) {
        return v == null || String.valueOf(v).trim().isEmpty();
    }

    private String trimTo(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return "";
        }
        int m = Math.max(0, maxLen);
        return t.length() <= m ? t : t.substring(0, m);
    }

    /**
     * 基于用户查询从向量数据库检索相关文档
     *
     * @param query 用户查询文本
     * @return 引用文档列表
     */
    private List<Map<String, Object>> buildCitations(String query, Map<String, Object> profile) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String enrichedQuery = enrichQuery(query, profile);
        Set<String> preferCities = extractCities(profile);
        String preferExperience = profile == null ? "" : asString(profile.get("experience"), "");

        try {
            SearchRequest.Builder reqBuilder = SearchRequest.builder().query(enrichedQuery).topK(20);
            String filter = buildMetadataFilterExpression(preferCities, preferExperience);
            if (!filter.isEmpty()) {
                reqBuilder.filterExpression(filter);
            }
            List<Document> docs = vectorStore.similaritySearch(reqBuilder.build());
            if (docs.isEmpty() && !filter.isEmpty()) {
                docs = vectorStore.similaritySearch(SearchRequest.builder().query(enrichedQuery).topK(20).build());
            }
            List<Map<String, Object>> raw = new ArrayList<>();
            for (Document d : docs) {
                Map<String, Object> meta = d.getMetadata() == null ? Map.of() : d.getMetadata();
                Map<String, Object> c = new HashMap<>();
                c.put("title", metaFirst(meta, "title", "job_name", "jobName") == null ? "参考片段" : metaFirst(meta, "title", "job_name", "jobName"));
                String src = metaString(meta, "", "source");
                if (src.isEmpty()) src = metaString(meta, "", "source_table", "sourceTable");
                c.put("source", src);
                Object sourceTableRaw = metaFirst(meta, "source_table", "sourceTable");
                if (sourceTableRaw != null) c.put("source_table", sourceTableRaw);
                Object jobIdRaw = metaFirst(meta, "job_id", "jobId", "id");
                if (jobIdRaw != null) c.put("job_id", jobIdRaw);
                Object jobUrl = metaFirst(meta, "job_url", "jobUrl", "url");
                if (jobUrl != null) c.put("job_url", jobUrl);
                Object jobName = metaFirst(meta, "job_name", "jobName");
                if (jobName != null) c.put("job_name", jobName);
                Object companyName = metaFirst(meta, "company_name", "companyName");
                if (companyName != null) c.put("company_name", companyName);
                Object city = metaFirst(meta, "city");
                if (city != null) c.put("city", city);
                Object experience = metaFirst(meta, "experience");
                if (experience != null) c.put("experience", experience);
                Object education = metaFirst(meta, "education");
                if (education != null) c.put("education", education);
                Object companyIndustry = metaFirst(meta, "company_industry", "companyIndustry");
                if (companyIndustry != null) c.put("company_industry", companyIndustry);
                Object companySize = metaFirst(meta, "company_size", "companySize");
                if (companySize != null) c.put("company_size", companySize);
                Object companyWelfare = metaFirst(meta, "company_welfare", "companyWelfare");
                if (companyWelfare != null) c.put("company_welfare", companyWelfare);
                Object publishDate = metaFirst(meta, "publish_date", "publishDate");
                if (publishDate != null) c.put("publish_date", publishDate);
                Object jobKeywords = metaFirst(meta, "job_keywords", "jobKeywords", "keywords");
                if (jobKeywords != null) c.put("job_keywords", jobKeywords);
                Object jobDesc = metaFirst(meta, "job_desc", "jobDesc", "description");
                if (jobDesc != null) c.put("job_desc", jobDesc);
                Integer salaryMin = asInteger(metaFirst(meta, "salary_min", "salaryMin", "minSalaryK", "minSalary"));
                Integer salaryMax = asInteger(metaFirst(meta, "salary_max", "salaryMax", "maxSalaryK", "maxSalary"));
                if (salaryMin != null) c.put("salaryMin", salaryMin);
                if (salaryMax != null) c.put("salaryMax", salaryMax);

                Long jobId = asLong(c.get("job_id"));
                String sourceTable = asString(c.get("source_table"), "");
                String source = asString(c.get("source"), "");
                if (sourceTable.isEmpty()) {
                    if ("51job".equalsIgnoreCase(source)) {
                        sourceTable = "job_info_51job";
                    } else if ("boss".equalsIgnoreCase(source)) {
                        sourceTable = "job_info";
                    }
                }

                boolean needEnrich =
                        isBlank(c.get("job_keywords")) ||
                        isBlank(c.get("job_desc")) ||
                        isBlank(c.get("company_industry")) ||
                        isBlank(c.get("company_size")) ||
                        isBlank(c.get("company_welfare")) ||
                        isBlank(c.get("publish_date")) ||
                        isBlank(c.get("job_url")) ||
                        c.get("salaryMin") == null ||
                        c.get("salaryMax") == null;

                if (needEnrich && jobId != null && !sourceTable.isEmpty()) {
                    try {
                        if ("job_info_51job".equalsIgnoreCase(sourceTable)) {
                            JobInfo51Job job = jobInfo51JobService.getById(jobId);
                            if (job != null) {
                                if (isBlank(c.get("job_url"))) c.put("job_url", job.getJobUrl());
                                if (isBlank(c.get("job_keywords"))) c.put("job_keywords", trimTo(job.getJobKeywords(), 2000));
                                if (isBlank(c.get("job_desc"))) c.put("job_desc", trimTo(job.getJobDesc(), 8000));
                                if (isBlank(c.get("company_industry"))) c.put("company_industry", trimTo(job.getCompanyIndustry(), 256));
                                if (isBlank(c.get("company_size"))) c.put("company_size", trimTo(job.getCompanySize(), 256));
                                if (isBlank(c.get("company_welfare"))) c.put("company_welfare", trimTo(job.getCompanyWelfare(), 2000));
                                if (isBlank(c.get("publish_date")) && job.getPublishDate() != null) c.put("publish_date", job.getPublishDate());
                                if (c.get("salaryMin") == null && job.getSalaryMin() != null) c.put("salaryMin", job.getSalaryMin());
                                if (c.get("salaryMax") == null && job.getSalaryMax() != null) c.put("salaryMax", job.getSalaryMax());
                            }
                        } else if ("job_info".equalsIgnoreCase(sourceTable)) {
                            JobInfo job = jobInfoService.getById(jobId);
                            if (job != null) {
                                if (isBlank(c.get("job_url"))) c.put("job_url", job.getJobUrl());
                                if (isBlank(c.get("job_keywords"))) c.put("job_keywords", trimTo(job.getJobKeywords(), 2000));
                                if (isBlank(c.get("job_desc"))) c.put("job_desc", trimTo(job.getJobDesc(), 8000));
                                if (isBlank(c.get("company_industry"))) c.put("company_industry", trimTo(job.getCompanyIndustry(), 256));
                                if (isBlank(c.get("company_size"))) c.put("company_size", trimTo(job.getCompanySize(), 256));
                                if (isBlank(c.get("company_welfare"))) c.put("company_welfare", trimTo(job.getCompanyWelfare(), 2000));
                                if (isBlank(c.get("publish_date")) && job.getPublishDate() != null) c.put("publish_date", job.getPublishDate());
                                if (c.get("salaryMin") == null && job.getSalaryMin() != null) c.put("salaryMin", job.getSalaryMin());
                                if (c.get("salaryMax") == null && job.getSalaryMax() != null) c.put("salaryMax", job.getSalaryMax());
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                double rerankScore = 0.0;
                if (!preferCities.isEmpty()) {
                    String docCity = asString(c.get("city"), "");
                    for (String pc : preferCities) {
                        if (!pc.isEmpty() && docCity.contains(pc)) {
                            rerankScore += 50.0;
                            break;
                        }
                    }
                }
                c.put("_score", rerankScore);

                String content = d.getText() == null ? "" : d.getText();
                c.put("snippet", content.length() > 240 ? content.substring(0, 240) : content);
                raw.add(c);
            }

            raw.sort((a, b) -> {
                double sa = ((Number) a.getOrDefault("_score", 0.0)).doubleValue();
                double sb = ((Number) b.getOrDefault("_score", 0.0)).doubleValue();
                return Double.compare(sb, sa);
            });

            List<Map<String, Object>> out = new ArrayList<>();
            int topN = Math.min(8, raw.size());
            for (int i = 0; i < topN; i++) {
                raw.get(i).remove("_score");
                out.add(raw.get(i));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildMetadataFilterExpression(Set<String> preferCities, String preferExperience) {
        String cityExpr = buildCityFilterExpression(preferCities);
        String expExpr = buildExperienceFilterExpression(preferExperience);
        if (cityExpr.isEmpty()) return expExpr;
        if (expExpr.isEmpty()) return cityExpr;
        return "(" + cityExpr + ") && (" + expExpr + ")";
    }

    private String buildCityFilterExpression(Set<String> preferCities) {
        if (preferCities == null || preferCities.isEmpty()) return "";
        Set<String> variants = new LinkedHashSet<>();
        for (String c : preferCities) {
            if (c == null) continue;
            String t = c.trim();
            if (t.isEmpty()) continue;
            variants.add(t);
            if (t.endsWith("市") && t.length() > 1) {
                variants.add(t.substring(0, t.length() - 1));
            } else {
                variants.add(t + "市");
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String v : variants) {
            if (sb.length() > 0) sb.append(" || ");
            sb.append("city == '").append(escapeFilterText(v)).append("'");
        }
        return sb.toString();
    }

    private String buildExperienceFilterExpression(String preferExperience) {
        String exp = preferExperience == null ? "" : preferExperience.trim();
        if (exp.isEmpty() || exp.contains("不限") || exp.contains("经验不限")) {
            return "";
        }

        Set<String> tokens = new LinkedHashSet<>();
        if (exp.contains("应届") || exp.contains("在校") || exp.contains("校招") || exp.contains("实习") || exp.contains("毕业")) {
            tokens.add("应届");
            tokens.add("应届生");
            tokens.add("在校");
            tokens.add("在校生");
            tokens.add("校招");
            tokens.add("实习");
            tokens.add("经验不限");
            tokens.add("不限");
            tokens.add("无经验");
            tokens.add("1年以内");
            tokens.add("1年以下");
            tokens.add("0-1年");
        } else {
            tokens.add(exp);
        }

        if (tokens.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("experience in [");
        boolean first = true;
        for (String t : tokens) {
            if (t == null || t.trim().isEmpty()) continue;
            if (!first) sb.append(", ");
            sb.append("'").append(escapeFilterText(t.trim())).append("'");
            first = false;
        }
        sb.append("]");
        return first ? "" : sb.toString();
    }

    private String escapeFilterText(String s) {
        if (s == null) return "";
        return s.replace("'", "\\'");
    }

    private String enrichQuery(String query, Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return query;
        }
        StringBuilder sb = new StringBuilder();
        Object targetRole = profile.get("targetRole");
        if (targetRole != null && !String.valueOf(targetRole).trim().isEmpty()) {
            sb.append(String.valueOf(targetRole).trim()).append(" ");
        }
        sb.append(query);
        Object city = profile.get("city");
        if (city != null && !String.valueOf(city).trim().isEmpty()) {
            sb.append(" ").append(String.valueOf(city).trim());
        }
        Object skills = profile.get("skills");
        if (skills != null && !String.valueOf(skills).trim().isEmpty()) {
            String[] parts = String.valueOf(skills).trim().split("[,，\\s]+");
            for (int i = 0; i < Math.min(3, parts.length); i++) {
                if (!parts[i].isBlank()) {
                    sb.append(" ").append(parts[i].trim());
                }
            }
        }
        return sb.toString().trim();
    }

    private Set<String> extractCities(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return Set.of();
        }
        Object city = profile.get("city");
        if (city == null) {
            return Set.of();
        }
        String s = String.valueOf(city).trim();
        if (s.isEmpty()) {
            return Set.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : s.split("[,，/\\s]+")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return set;
    }

    private List<Map<String, Object>> fetchCandidates(String userMessage, Map<String, Object> profile) {
        Map<String, Object> args = buildJobSearchArgs(userMessage, profile);
        return fetchCandidatesByRag(userMessage, profile, args);
    }

    private List<Map<String, Object>> fetchCandidatesByRag(String userMessage, Map<String, Object> profile, Map<String, Object> args) {
        String source = asString(args.get("source"), "all");
        String keyword = asString(args.get("keyword"), "");
        String city = asString(args.get("city"), "");
        String education = asString(args.get("education"), "");
        String experience = asString(args.get("experience"), "");
        Integer minSalaryK = asInteger(args.get("minSalaryK"));
        Integer maxSalaryK = asInteger(args.get("maxSalaryK"));
        String company = asString(args.get("company"), "");
        Integer limit = asInteger(args.get("limit"));
        int topK = capTopK(limit);

        String query = buildCandidateRagQuery(userMessage, profile, args);
        String filter = buildCandidateFilterExpression(source, city, education, experience);

        try {
            SearchRequest.Builder reqBuilder = SearchRequest.builder().query(query).topK(topK);
            if (!filter.isEmpty()) {
                reqBuilder.filterExpression(filter);
            }
            List<Document> docs = vectorStore.similaritySearch(reqBuilder.build());
            if ((docs == null || docs.isEmpty()) && !filter.isEmpty()) {
                docs = vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
            }
            List<Map<String, Object>> candidates = docsToJobCandidates(docs, topK);
            if (candidates != null && !candidates.isEmpty()) {
                return candidates;
            }
        } catch (Exception ignored) {
        }

        return List.of();
    }

    private int capTopK(Integer limit) {
        int def = 30;
        int v = limit == null ? def : limit;
        int capped = Math.max(1, Math.min(CANDIDATE_TOPK_MAX, v));
        return capped;
    }

    private String buildCandidateRagQuery(String userMessage, Map<String, Object> profile, Map<String, Object> args) {
        String keyword = asString(args == null ? null : args.get("keyword"), "");
        String company = asString(args == null ? null : args.get("company"), "");
        String city = asString(args == null ? null : args.get("city"), "");
        Integer minSalaryK = asInteger(args == null ? null : args.get("minSalaryK"));
        Integer maxSalaryK = asInteger(args == null ? null : args.get("maxSalaryK"));

        StringBuilder sb = new StringBuilder();
        if (!keyword.isEmpty()) sb.append(keyword).append(" ");
        if (!company.isEmpty()) sb.append(company).append(" ");
        if (!city.isEmpty()) sb.append(city).append(" ");
        if (minSalaryK != null || maxSalaryK != null) {
            sb.append("薪资 ");
            if (minSalaryK != null) sb.append(minSalaryK).append("K以上 ");
            if (maxSalaryK != null) sb.append(maxSalaryK).append("K以内 ");
        }
        String p = renderProfile(profile);
        if (!p.isEmpty()) sb.append(p).append(" ");
        if (userMessage != null && !userMessage.trim().isEmpty()) sb.append(userMessage.trim());
        String q = sb.toString().trim();
        return q.isEmpty() ? (userMessage == null ? "" : userMessage.trim()) : q;
    }

    private String buildCandidateFilterExpression(String source, String city, String education, String experience) {
        List<String> parts = new ArrayList<>();
        String src = source == null ? "" : source.trim().toLowerCase();
        if ("boss".equals(src) || "51job".equals(src)) {
            parts.add("source == '" + escapeFilterText(src) + "'");
        }
        Set<String> cities = parseCities(city);
        String cityExpr = buildCityFilterExpression(cities);
        if (!cityExpr.isEmpty()) {
            parts.add("(" + cityExpr + ")");
        }
        String eduExpr = buildEducationFilterExpression(education);
        if (!eduExpr.isEmpty()) {
            parts.add(eduExpr);
        }
        String expExpr = buildExperienceFilterExpression(experience);
        if (!expExpr.isEmpty()) {
            parts.add(expExpr);
        }
        if (parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(" && ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private String buildEducationFilterExpression(String education) {
        String edu = education == null ? "" : education.trim();
        if (edu.isEmpty() || edu.contains("不限")) {
            return "";
        }
        return "education == '" + escapeFilterText(edu) + "'";
    }

    private Set<String> parseCities(String city) {
        if (city == null || city.trim().isEmpty()) return Set.of();
        Set<String> set = new LinkedHashSet<>();
        for (String part : city.split("[,，/\\s]+")) {
            String t = part == null ? "" : part.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    private List<Map<String, Object>> docsToJobCandidates(List<Document> docs, int limit) {
        if (docs == null || docs.isEmpty()) return List.of();
        int lim = Math.max(1, Math.min(CANDIDATE_TOPK_MAX, limit));

        Map<String, Map<String, Object>> outMap = new LinkedHashMap<>();
        for (Document d : docs) {
            if (d == null) continue;
            Map<String, Object> card = docToJobCard(d);
            if (card == null || card.isEmpty()) continue;
            String key = candidateKey(card);
            if (key.isEmpty() || outMap.containsKey(key)) continue;
            outMap.put(key, card);
            if (outMap.size() >= lim) break;
        }
        return new ArrayList<>(outMap.values());
    }

    private String candidateKey(Map<String, Object> card) {
        if (card == null) return "";
        String source = asString(card.get("source"), "");
        Long id = asLong(card.get("id"));
        if (!source.isEmpty() && id != null) {
            return source + ":" + id;
        }
        String jobName = asString(card.get("jobName"), "");
        String companyName = asString(card.get("companyName"), "");
        String city = asString(card.get("city"), "");
        if (!jobName.isEmpty() || !companyName.isEmpty()) {
            return jobName + "|" + companyName + "|" + city;
        }
        return "";
    }

    private Map<String, Object> docToJobCard(Document d) {
        Map<String, Object> meta = d.getMetadata() == null ? Map.of() : d.getMetadata();
        Map<String, Object> card = new HashMap<>();

        String source = metaString(meta, "", "source");
        String sourceTable = metaString(meta, "", "source_table", "sourceTable");
        Long jobId = asLong(metaFirst(meta, "job_id", "jobId", "id"));
        if (sourceTable.isEmpty()) {
            if ("51job".equalsIgnoreCase(source)) {
                sourceTable = "job_info_51job";
            } else if ("boss".equalsIgnoreCase(source)) {
                sourceTable = "job_info";
            }
        }

        card.put("source", source);
        if (jobId != null) card.put("id", jobId);
        card.put("jobName", metaString(meta, "", "job_name", "jobName"));
        card.put("companyName", metaString(meta, "", "company_name", "companyName"));
        card.put("city", metaString(meta, "", "city"));
        card.put("jobUrl", metaString(meta, "", "job_url", "jobUrl", "url"));
        card.put("experience", metaString(meta, "", "experience"));
        card.put("education", metaString(meta, "", "education"));
        card.put("jobDesc", trimTo(metaString(meta, "", "job_desc", "jobDesc", "description"), 8000));
        card.put("jobKeywords", trimTo(metaString(meta, "", "job_keywords", "jobKeywords", "keywords"), 2000));
        card.put("companyIndustry", trimTo(metaString(meta, "", "company_industry", "companyIndustry"), 256));
        card.put("companySize", trimTo(metaString(meta, "", "company_size", "companySize"), 256));
        card.put("companyWelfare", trimTo(metaString(meta, "", "company_welfare", "companyWelfare"), 2000));
        card.put("publishDate", metaFirst(meta, "publish_date", "publishDate"));

        Integer salaryMin = asInteger(metaFirst(meta, "salary_min", "salaryMin", "minSalaryK", "minSalary"));
        Integer salaryMax = asInteger(metaFirst(meta, "salary_max", "salaryMax", "maxSalaryK", "maxSalary"));
        if (salaryMin != null) card.put("salaryMin", salaryMin);
        if (salaryMax != null) card.put("salaryMax", salaryMax);

        if (isBlank(card.get("jobName"))) {
            String title = metaString(meta, "", "title");
            if (!title.isEmpty()) card.put("jobName", title);
        }

        return card;
    }

    private Map<String, Object> buildJobSearchArgs(String userMessage, Map<String, Object> profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个岗位检索条件提取器。只输出一个 JSON Object，不要任何解释文字，不要代码块。");
        sb.append("你需要从用户问题与用户画像中提取用于向量检索/RAG 的硬约束。");
        sb.append("\n字段：source, keyword, city, education, experience, minSalaryK, maxSalaryK, company, limit。");
        sb.append("\n规则：");
        sb.append("\n- source 只允许 boss/51job/all（不确定就 all）。");
        sb.append("\n- city 支持逗号分隔多个城市；优先使用画像 city。");
        sb.append("\n- keyword 优先使用画像 targetRole 或用户明确提到的岗位方向。");
        sb.append("\n- minSalaryK/maxSalaryK 是月薪(K)整数，不确定就省略或 null。");
        sb.append("\n- education/experience 如用户明确提出再填。");
        sb.append("\n- limit 默认 30，最大 50。");

        String p = renderProfile(profile);
        String user = (p.isEmpty() ? "" : ("用户画像：\n" + p + "\n\n")) + "用户问题：\n" + userMessage;

        String raw = "";
        try {
            raw = chatClient.prompt().system(sb.toString()).user(user).call().content();
        } catch (Exception ignored) {
        }

        Map<String, Object> args = parseArgsJsonLenient(raw);
        if (args == null) {
            args = new HashMap<>();
        }
        if (!args.containsKey("city")) {
            Object city = profile == null ? null : profile.get("city");
            if (city != null) args.put("city", String.valueOf(city));
        }
        if (!args.containsKey("keyword")) {
            Object tr = profile == null ? null : profile.get("targetRole");
            if (tr != null) args.put("keyword", String.valueOf(tr));
        }
        if (!args.containsKey("source")) {
            args.put("source", "all");
        }
        if (!args.containsKey("limit")) {
            args.put("limit", 30);
        }
        return args;
    }

    private List<Map<String, Object>> selectRecommendations(String userMessage, Map<String, Object> profile, List<Map<String, Object>> citations, List<Map<String, Object>> candidates, int topN) {
        int n = Math.max(1, Math.min(10, topN));
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String candidatesText = renderCandidatesForSelection(candidates, 30);
        String p = renderProfile(profile);
        String citationsText = renderCitationsForPrompt(citations, 5);

        String system = ""
                + "你是岗位推荐精排器。你的任务：只从候选岗位列表中选择最匹配的岗位，并输出严格 JSON。"
                + "\n要求："
                + "\n- 必须满足用户画像与用户问题中的硬约束（城市/薪资/学历/经验等）；不满足的不要选。"
                + "\n- 语义参考资料仅用于理解方向与同义词，不得引入候选列表之外的岗位。"
                + "\n- 只输出一个 JSON Object，不要解释，不要代码块。"
                + "\nJSON 格式：{\"selected\":[{\"index\":1,\"score\":85,\"reason\":\"...\"}]}，index 为候选序号（从1开始），score 为0-100整数。"
                + "\n- selected 长度为 " + n + "（若候选不足则尽量多选，但不得重复）。";

        String user = ""
                + (p.isEmpty() ? "" : ("用户画像：\n" + p + "\n\n"))
                + "用户问题：\n" + userMessage + "\n\n"
                + (citationsText.isEmpty() ? "" : ("语义参考资料：\n" + citationsText + "\n\n"))
                + "候选岗位（向量检索候选）：\n" + candidatesText;

        String raw = "";
        try {
            raw = chatClient.prompt().system(system).user(user).call().content();
        } catch (Exception ignored) {
        }

        Map<String, Object> obj = parseArgsJsonLenient(raw);
        Object selectedObj = obj == null ? null : obj.get("selected");
        if (!(selectedObj instanceof List)) {
            return fallbackPickTop(candidates, n);
        }
        List<?> selected = (List<?>) selectedObj;
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (Object it : selected) {
            if (!(it instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) it;
            Integer index = asInteger(m.get("index"));
            if (index == null) continue;
            int idx = index - 1;
            if (idx < 0 || idx >= candidates.size()) continue;
            if (used.contains(index)) continue;
            used.add(index);

            Map<String, Object> base = new HashMap<>();
            Map<String, Object> cand = candidates.get(idx);
            if (cand != null) {
                base.putAll(cand);
            }
            Integer score = asInteger(m.get("score"));
            String reason = asString(m.get("reason"), "");
            if (score != null) base.put("matchScore", score);
            if (!reason.isEmpty()) base.put("aiReason", reason);
            out.add(base);
            if (out.size() >= n) break;
        }
        if (out.isEmpty()) {
            return fallbackPickTop(candidates, n);
        }
        return out;
    }

    private List<Map<String, Object>> fallbackPickTop(List<Map<String, Object>> candidates, int topN) {
        int n = Math.max(1, Math.min(10, topN));
        List<Map<String, Object>> out = new ArrayList<>();
        int end = Math.min(n, candidates == null ? 0 : candidates.size());
        for (int i = 0; i < end; i++) {
            out.add(new HashMap<>(candidates.get(i)));
        }
        return out;
    }

    private String renderCandidatesForSelection(List<Map<String, Object>> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return "(无)";
        }
        int shown = Math.min(Math.max(1, limit), candidates.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            Map<String, Object> j = candidates.get(i);
            String jobName = asString(j.get("jobName"), "");
            String companyName = asString(j.get("companyName"), "");
            String city = asString(j.get("city"), "");
            Integer salaryMin = asInteger(j.get("salaryMin"));
            Integer salaryMax = asInteger(j.get("salaryMax"));
            String salary = (salaryMin != null && salaryMax != null) ? (salaryMin + "-" + salaryMax + "K") : "面议";
            String experience = asString(j.get("experience"), "");
            String education = asString(j.get("education"), "");
            String source = asString(j.get("source"), "");
            sb.append(i + 1).append(". ")
                    .append(jobName.isEmpty() ? "（未命名岗位）" : jobName)
                    .append(companyName.isEmpty() ? "" : (" · " + companyName))
                    .append(city.isEmpty() ? "" : (" · " + city))
                    .append(" · ").append(salary)
                    .append(experience.isEmpty() ? "" : (" · " + experience))
                    .append(education.isEmpty() ? "" : (" · " + education))
                    .append(source.isEmpty() ? "" : (" · " + source))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String renderCitationsForPrompt(List<Map<String, Object>> citations, int limit) {
        if (citations == null || citations.isEmpty()) {
            return "";
        }
        int shown = Math.min(Math.max(1, limit), citations.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < shown; i++) {
            Map<String, Object> c = citations.get(i);
            String title = String.valueOf(c.getOrDefault("title", ""));
            String snippet = String.valueOf(c.getOrDefault("snippet", ""));
            if (title.isBlank() && snippet.isBlank()) continue;
            sb.append(i + 1).append(". ").append(title).append("\n");
            if (!snippet.isBlank()) sb.append(snippet).append("\n");
        }
        return sb.toString().trim();
    }

    private String generateFinalReply(String userMessage, Map<String, Object> profile, List<Map<String, Object>> citations, List<Map<String, Object>> recommended) {
        FinalReplyPrompt prompt = buildFinalReplyPrompt(userMessage, profile, citations, recommended);
        try {
            return chatClient.prompt().system(prompt.system()).user(prompt.user()).call().content();
        } catch (Exception e) {
            return "本次推荐已生成，但输出建议时发生异常。你可以缩短问题或减少约束条件后重试。";
        }
    }

    private record FinalReplyPrompt(String system, String user) {
    }

    private FinalReplyPrompt buildFinalReplyPrompt(String userMessage, Map<String, Object> profile, List<Map<String, Object>> citations, List<Map<String, Object>> recommended) {
        String p = renderProfile(profile);
        String citationsText = renderCitationsForPrompt(citations, 5);
        String selectedText = renderCandidatesForSelection(recommended, 10);

        String system = ""
                + "你是招聘数据分析系统内置的智能体。你将综合三类信息输出最终岗位推荐："
                + "\n1) 语义参考资料（仅作背景证据）"
                + "\n2) RAG候选检索并精排后的最终推荐列表（必须以此为准）"
                + "\n3) 你的通用求职知识（用于给建议与行动清单）"
                + "\n要求："
                + "\n- 中文输出，结构清晰。"
                + "\n- 不要编造岗位；岗位名称/公司/城市必须来自最终推荐列表。"
                + "\n- 给出每个岗位的推荐理由、适配点与差距点、投递/面试准备建议。";

        String user = ""
                + (p.isEmpty() ? "" : ("用户画像：\n" + p + "\n\n"))
                + "用户问题：\n" + userMessage + "\n\n"
                + (citationsText.isEmpty() ? "" : ("语义参考资料：\n" + citationsText + "\n\n"))
                + "最终推荐岗位（用于页面卡片展示，必须以此为准）：\n" + selectedText;

        return new FinalReplyPrompt(system, user);
    }

    /**
     * 将用户消息和历史记录格式化为纯文本
     *
     * @param request 聊天请求
     * @return 格式化后的用户消息文本
     */
    private String buildUserMessage(AiChatRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request != null && request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (Map<String, String> h : request.getHistory()) {
                if (h == null) {
                    continue;
                }
                String role = h.get("role");
                String content = h.get("content");
                if (role == null || content == null) {
                    continue;
                }
                String r = role.trim().toLowerCase();
                if (!("user".equals(r) || "assistant".equals(r))) {
                    continue;
                }
                sb.append("user".equals(r) ? "用户: " : "助手: ").append(content).append("\n");
            }
        }
        if (request != null && request.getMessage() != null && !request.getMessage().trim().isEmpty()) {
            sb.append("用户: ").append(request.getMessage().trim());
        }
        return sb.toString().trim();
    }

    /**
     * 将用户画像格式化为文本
     */
    private String renderProfile(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String k : List.of("targetRole", "city", "education", "experience", "skills", "notes")) {
            Object v = profile.get(k);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) {
                sb.append(k).append(": ").append(s).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
