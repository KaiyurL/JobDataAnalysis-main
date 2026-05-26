package com.jobdata.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.context.UserContextHolder;
import com.jobdata.ai.tools.JobTools;
import com.jobdata.ai.tools.JobToolResultStore;
import com.jobdata.ai.tools.UserTools;
import com.jobdata.dto.AiChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AgentChatService {

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(.*?)\\s*```");
    private static final Pattern MISSING_VALUE_COMMA = Pattern.compile(":\\s*,");
    private static final Pattern MISSING_VALUE_END_OBJ = Pattern.compile(":\\s*}");

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final JobTools jobTools;
    private final UserTools userTools;
    private final JobToolResultStore jobToolResultStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentChatService(ChatClient.Builder builder, VectorStore vectorStore, JobTools jobTools, UserTools userTools, JobToolResultStore jobToolResultStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
        this.jobTools = jobTools;
        this.userTools = userTools;
        this.jobToolResultStore = jobToolResultStore;
    }

    public AgentChatResponse chatOnce(AiChatRequest request, Long userId) {
        Long prevUserId = UserContextHolder.getUserId();
        UserContextHolder.setUserId(userId);
        try {
            String userMessage = buildUserMessage(request);
            String system = buildSystemPrompt(request, userId, userMessage);

            String reply;
            try {
                reply = chatClient
                        .prompt()
                        .system(system)
                        .user(userMessage)
                        .tools(jobTools, userTools)
                        .call()
                        .content();
            } catch (RuntimeException e) {
                if (isToolArgumentsJsonError(e)) {
                    return fallbackManualJobSearch(request, userId, userMessage, system);
                }
                throw e;
            }

            AgentChatResponse out = new AgentChatResponse();
            out.setReply(reply == null ? "" : reply);
            out.setJobCards(jobToolResultStore.consumeLastJobCards());
            out.setCitations(buildCitations(userMessage));
            return out;
        } finally {
            if (prevUserId == null) {
                UserContextHolder.clear();
            } else {
                UserContextHolder.setUserId(prevUserId);
            }
        }
    }

    public Flux<ServerSentEvent<AgentStreamEvent>> chatStream(AiChatRequest request, Long userId) {
        AgentChatResponse full = chatOnce(request, userId);
        String text = full.getReply() == null ? "" : full.getReply();

        Flux<ServerSentEvent<AgentStreamEvent>> start = Flux.just(ServerSentEvent.builder(new AgentStreamEvent("start", "")).build());
        Flux<ServerSentEvent<AgentStreamEvent>> tokens = Flux.fromIterable(splitForStream(text, 32))
                .delayElements(Duration.ofMillis(25))
                .map(t -> ServerSentEvent.builder(new AgentStreamEvent("delta", t)).build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("citations", full.getCitations());
        payload.put("jobCards", full.getJobCards());
        Flux<ServerSentEvent<AgentStreamEvent>> end = Flux.just(ServerSentEvent.builder(new AgentStreamEvent("end", "", payload)).build());

        return start.concatWith(tokens).concatWith(end).timeout(Duration.ofSeconds(180));
    }

    private List<String> splitForStream(String s, int chunkSize) {
        if (s == null || s.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, chunkSize);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i += size) {
            out.add(s.substring(i, Math.min(s.length(), i + size)));
        }
        return out;
    }

    private String buildSystemPrompt(AiChatRequest request, Long userId, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个招聘数据分析系统内置的智能体，负责基于真实数据库岗位与用户画像给出建议。");
        sb.append("要求：中文输出；结构清晰；不编造岗位；当需要岗位数据时应调用工具 job_search；当需要用户数据时应调用 user_* 工具。");
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
        sb.append("\n- 用户问“我上次匹配了什么/匹配历史”时：先调用 user_list_match_history（如系统未提供匹配摘要）。");
        sb.append("\n- 仅当用户明确要求“保存/更新画像”时才调用 user_upsert_profile。");
        sb.append("\n\n【可用工具】");
        sb.append("\n- job_search：按条件从数据库检索岗位。");
        sb.append("\n- user_get_profile：读取当前用户画像。");
        sb.append("\n- user_list_favorites：读取收藏。");
        sb.append("\n- user_list_job_history：读取浏览历史。");
        sb.append("\n- user_list_match_history：读取匹配历史。");
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
            if (needMatchHistory(query)) {
                String mh = renderMatchHistoryForPromptSafe(10);
                if (!mh.isEmpty()) {
                    sb.append("\n\n【用户匹配历史（节选）】\n").append(mh);
                }
            }
        }

        List<Map<String, Object>> citations = buildCitations(query);
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

    private boolean needFavorites(String query) {
        return containsAny(query, "收藏", "喜欢", "偏好");
    }

    private boolean needJobHistory(String query) {
        return containsAny(query, "浏览", "看过", "最近看", "历史记录");
    }

    private boolean needMatchHistory(String query) {
        return containsAny(query, "匹配历史", "上次匹配", "之前匹配", "匹配记录");
    }

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

    private String renderFavoritesForPromptSafe(int limit) {
        try {
            List<Map<String, Object>> list = userTools.userListFavorites(limit);
            return renderJobListForPrompt(list, limit);
        } catch (Exception e) {
            return "";
        }
    }

    private String renderJobHistoryForPromptSafe(int limit) {
        try {
            List<Map<String, Object>> list = userTools.userListJobHistory(limit);
            return renderJobListForPrompt(list, limit);
        } catch (Exception e) {
            return "";
        }
    }

    private String renderMatchHistoryForPromptSafe(int limit) {
        try {
            List<Map<String, Object>> list = userTools.userListMatchHistory(limit);
            if (list == null || list.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            int shown = Math.min(limit, list.size());
            for (int i = 0; i < shown; i++) {
                Map<String, Object> m = list.get(i);
                String targetRole = asString(m == null ? null : m.get("targetRole"), "");
                String city = asString(m == null ? null : m.get("city"), "");
                String createdAt = String.valueOf(m == null ? "" : m.getOrDefault("createdAt", ""));
                if (targetRole.isEmpty() && city.isEmpty()) {
                    continue;
                }
                sb.append(i + 1).append(". ")
                        .append(targetRole.isEmpty() ? "（未填目标岗位）" : targetRole)
                        .append(city.isEmpty() ? "" : (" · " + city))
                        .append(createdAt.isBlank() ? "" : (" · " + createdAt))
                        .append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

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

    private boolean isToolArgumentsJsonError(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                if (msg.contains("Conversion from JSON") || msg.contains("JsonParseException") || msg.contains("Unexpected character")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private AgentChatResponse fallbackManualJobSearch(AiChatRequest request, Long userId, String userMessage, String system) {
        String extractSystem = system
                + "\n\n现在工具调用发生了参数 JSON 解析错误。"
                + "请你改为输出一个“岗位检索参数 JSON”，要求只输出一个 JSON Object，不要任何解释文字，不要代码块。"
                + "字段：source, keyword, city, education, experience, minSalaryK, maxSalaryK, company, limit。"
                + "不确定就省略字段或写 null。";

        String argsJson = "";
        try {
            argsJson = chatClient.prompt().system(extractSystem).user(userMessage).call().content();
        } catch (Exception ignored) {
        }

        Map<String, Object> args = parseArgsJsonLenient(argsJson);
        String source = asString(args.get("source"), "all");
        String keyword = asString(args.get("keyword"), "");
        String city = asString(args.get("city"), "");
        String education = asString(args.get("education"), "");
        String experience = asString(args.get("experience"), "");
        Integer minSalaryK = asInteger(args.get("minSalaryK"));
        Integer maxSalaryK = asInteger(args.get("maxSalaryK"));
        String company = asString(args.get("company"), "");
        Integer limit = asInteger(args.get("limit"));

        List<Map<String, Object>> jobs = jobTools.jobSearch(source, keyword, city, education, experience, minSalaryK, maxSalaryK, company, limit);

        StringBuilder candidates = new StringBuilder();
        int shown = Math.min(10, jobs == null ? 0 : jobs.size());
        for (int i = 0; i < shown; i++) {
            Map<String, Object> j = jobs.get(i);
            candidates.append(i + 1).append(". ")
                    .append(String.valueOf(j.getOrDefault("jobName", ""))).append(" - ")
                    .append(String.valueOf(j.getOrDefault("companyName", ""))).append("（")
                    .append(String.valueOf(j.getOrDefault("city", ""))).append("）")
                    .append(" 薪资: ")
                    .append(String.valueOf(j.getOrDefault("salaryMin", ""))).append("-")
                    .append(String.valueOf(j.getOrDefault("salaryMax", ""))).append("K")
                    .append(" 来源: ").append(String.valueOf(j.getOrDefault("source", "")))
                    .append("\n");
        }

        String finalSystem = system + "\n\n注意：本轮禁止再调用任何工具。请仅基于候选岗位与参考资料回答。";
        String finalUser = userMessage
                + "\n\n候选岗位（来自数据库检索）：\n"
                + (candidates.length() == 0 ? "(无结果)" : candidates.toString())
                + "\n请给出推荐与筛选建议。";

        String finalReply = "";
        try {
            finalReply = chatClient.prompt().system(finalSystem).user(finalUser).call().content();
        } catch (Exception e) {
            finalReply = "本次岗位检索已完成，但生成建议时发生异常。你可以换一种表述，或减少筛选条件重试。";
        }

        AgentChatResponse out = new AgentChatResponse();
        out.setReply(finalReply == null ? "" : finalReply);
        out.setJobCards(jobToolResultStore.consumeLastJobCards());
        out.setCitations(buildCitations(userMessage));
        return out;
    }

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

    private String asString(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

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

    private List<Map<String, Object>> buildCitations(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        try {
            SearchRequest req = SearchRequest.builder().query(query).topK(5).build();
            List<Document> docs = vectorStore.similaritySearch(req);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Document d : docs) {
                Map<String, Object> meta = d.getMetadata() == null ? Map.of() : d.getMetadata();
                Map<String, Object> c = new HashMap<>();
                c.put("title", meta.getOrDefault("title", meta.getOrDefault("job_name", "参考片段")));
                c.put("source", meta.getOrDefault("source", meta.getOrDefault("source_table", "")));
                c.put("job_id", meta.getOrDefault("job_id", meta.getOrDefault("id", null)));
                c.put("job_url", meta.getOrDefault("job_url", null));
                String content = d.getText() == null ? "" : d.getText();
                c.put("snippet", content.length() > 240 ? content.substring(0, 240) : content);
                out.add(c);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

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
