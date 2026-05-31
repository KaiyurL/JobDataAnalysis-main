package com.jobdata.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.ai.controller.AgentController;
import com.jobdata.ai.controller.RagAdminController;
import com.jobdata.ai.model.AgentChatResponse;
import com.jobdata.ai.model.AgentStreamEvent;
import com.jobdata.ai.rag.JobRagIndexer;
import com.jobdata.ai.rag.RagReindexJobManager;
import com.jobdata.ai.service.AgentChatService;
import com.jobdata.controller.*;
import com.jobdata.dto.AiChatRequest;
import com.jobdata.entity.User;
import com.jobdata.entity.User;
import com.jobdata.service.*;
import com.jobdata.util.JwtUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ApiFullSmokeTest {

    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                1L,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    record ApiCase(String httpMethod, String pathTemplate, Method handler, boolean multipart, Class<?> requestBodyType, Map<String, String> requiredParams, String produces) {}

    @Test
    void run_all_api_smoke_and_generate_report() throws Exception {
        JobInfoService jobInfoService = mock(JobInfoService.class);
        JobInfo51JobService jobInfo51JobService = mock(JobInfo51JobService.class);
        UserProfileService userProfileService = mock(UserProfileService.class);
        UserFavoriteJobService userFavoriteJobService = mock(UserFavoriteJobService.class);
        UserJobHistoryService userJobHistoryService = mock(UserJobHistoryService.class);
        UserService userService = mock(UserService.class);
        ResumeService resumeService = mock(ResumeService.class);
        PipelineService pipelineService = mock(PipelineService.class);
        DataManageService dataManageService = mock(DataManageService.class);
        ConfigService configService = mock(ConfigService.class);
        AgentChatService agentChatService = mock(AgentChatService.class);
        JobRagIndexer jobRagIndexer = mock(JobRagIndexer.class);
        RagReindexJobManager ragReindexJobManager = mock(RagReindexJobManager.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        stubServices(resumeService, pipelineService, dataManageService, configService, jobRagIndexer, ragReindexJobManager, agentChatService, userService, jwtUtil, passwordEncoder);

        AuthController authController = new AuthController();
        setField(authController, "userService", userService);
        setField(authController, "jwtUtil", jwtUtil);
        setField(authController, "passwordEncoder", passwordEncoder);

        UserController userController = new UserController();
        setField(userController, "userProfileService", userProfileService);
        setField(userController, "userFavoriteJobService", userFavoriteJobService);
        setField(userController, "userJobHistoryService", userJobHistoryService);
        setField(userController, "objectMapper", objectMapper);

        ResumeController resumeController = new ResumeController();
        setField(resumeController, "resumeService", resumeService);

        PipelineController pipelineController = new PipelineController();
        setField(pipelineController, "pipelineService", pipelineService);

        JobInfoController jobInfoController = new JobInfoController();
        setField(jobInfoController, "jobInfoService", jobInfoService);

        JobInfo51JobController jobInfo51JobController = new JobInfo51JobController();
        setField(jobInfo51JobController, "jobInfo51JobService", jobInfo51JobService);

        DataManageController dataManageController = new DataManageController();
        setField(dataManageController, "dataManageService", dataManageService);

        ConfigController configController = new ConfigController();
        setField(configController, "configService", configService);

        AgentController agentController = new AgentController(agentChatService);
        RagAdminController ragAdminController = new RagAdminController(jobRagIndexer, ragReindexJobManager);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(
                        authController,
                        userController,
                        resumeController,
                        pipelineController,
                        jobInfoController,
                        jobInfo51JobController,
                        dataManageController,
                        configController,
                        agentController,
                        ragAdminController
                )
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(),
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();

        List<ApiCase> cases = collectCases(List.of(
                AuthController.class,
                UserController.class,
                ResumeController.class,
                PipelineController.class,
                JobInfoController.class,
                JobInfo51JobController.class,
                DataManageController.class,
                ConfigController.class,
                AgentController.class,
                RagAdminController.class
        ));

        Assertions.assertFalse(cases.isEmpty(), "未发现任何接口用例");

        List<Map<String, Object>> results = new ArrayList<>();
        for (ApiCase c : cases) {
            String path = fillPathVariables(c.pathTemplate());
            RequestBuilder rb = buildRequest(c, path);
            long t0 = System.nanoTime();
            MvcResult r = mockMvc.perform(rb).andReturn();
            long ms = Math.max(0, (System.nanoTime() - t0) / 1_000_000);

            int status = r.getResponse().getStatus();
            String ct = normalizeContentType(r.getResponse().getContentType());
            String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
            String preview = body == null ? "" : (body.length() > 280 ? body.substring(0, 280) + "…" : body);

            boolean ok = status >= 200 && status < 300;
            boolean isPipelineFile = c.handler().getDeclaringClass() == PipelineController.class && "file".equals(c.handler().getName());
            if (ok && !isPipelineFile && ct != null && ct.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
                ok = body != null && body.contains("\"code\"");
            }

            results.add(new LinkedHashMap<>(Map.of(
                    "method", c.httpMethod(),
                    "path", path,
                    "handler", c.handler().getDeclaringClass().getSimpleName() + "#" + c.handler().getName(),
                    "status", status,
                    "contentType", ct == null ? "" : ct,
                    "ok", ok,
                    "latencyMs", ms,
                    "bodyPreview", preview
            )));
        }

        results.sort(Comparator
                .comparing((Map<String, Object> m) -> String.valueOf(m.get("path")))
                .thenComparing(m -> String.valueOf(m.get("method"))));

        long okCount = results.stream().filter(m -> Boolean.TRUE.equals(m.get("ok"))).count();
        long total = results.size();
        writeReport(results, okCount, total);

        List<Map<String, Object>> failed = results.stream().filter(m -> !Boolean.TRUE.equals(m.get("ok"))).toList();
        if (!failed.isEmpty()) {
            Assertions.fail("存在失败接口用例（展示首个失败用例）: " + objectMapper.writeValueAsString(failed.get(0)));
        }
    }

    private static void stubServices(
            ResumeService resumeService,
            PipelineService pipelineService,
            DataManageService dataManageService,
            ConfigService configService,
            JobRagIndexer jobRagIndexer,
            RagReindexJobManager ragReindexJobManager,
            AgentChatService agentChatService,
            UserService userService,
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder
    ) throws Exception {
        when(resumeService.parseResumeFileToText(ArgumentMatchers.any(MultipartFile.class))).thenReturn("hello");
        when(resumeService.extractProfileFromText(ArgumentMatchers.anyString())).thenReturn("{\"name\":\"test\"}");

        Path tmp = Files.createTempFile("api-test", ".json");
        Files.writeString(tmp, "{\"ok\":true}", StandardCharsets.UTF_8);
        when(pipelineService.getArtifactFile(anyString())).thenReturn(tmp.toFile());
        when(pipelineService.getPipelineStatus()).thenReturn(Map.of("status", "ok"));
        when(pipelineService.getPipelineArtifacts()).thenReturn(Map.of("items", List.of()));
        when(pipelineService.startDashboardPipeline(anyBoolean())).thenReturn(Map.of("started", true));
        when(pipelineService.startStatsPipeline(anyBoolean())).thenReturn(Map.of("started", true));

        when(dataManageService.getDataOverview()).thenReturn(Map.of("ok", true));
        when(dataManageService.startUpdate()).thenReturn(Map.of("ok", true));
        when(dataManageService.confirmLogin()).thenReturn(Map.of("ok", true));
        when(dataManageService.stopUpdate()).thenReturn(Map.of("ok", true));
        when(dataManageService.clearLogs()).thenReturn(Map.of("ok", true));

        when(configService.getConfig()).thenReturn(Map.of("ok", true));
        when(configService.updateConfig(ArgumentMatchers.anyMap())).thenReturn(Map.of("ok", true));

        when(jobRagIndexer.reindexJobs(anyString(), anyInt(), anyBoolean())).thenReturn(Map.of("ok", true, "indexed", 0));
        when(ragReindexJobManager.start(anyString(), anyInt(), any())).thenReturn(Map.of("jobId", "1", "status", "running"));
        when(ragReindexJobManager.status(any())).thenReturn(Map.of("jobId", "1", "status", "done", "result", Map.of("documents", 0)));

        AgentChatResponse chatResp = new AgentChatResponse();
        chatResp.setReply("ok");
        when(agentChatService.chatOnce(any(AiChatRequest.class), any())).thenReturn(chatResp);
        when(agentChatService.chatStream(any(AiChatRequest.class), any())).thenReturn(
                reactor.core.publisher.Flux.just(
                        ServerSentEvent.builder(new AgentStreamEvent("start", "")).build(),
                        ServerSentEvent.builder(new AgentStreamEvent("end", "")).build()
                )
        );

        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(jwtUtil.generateToken(anyString(), anyLong(), anyString())).thenReturn("test-token");

        when(userService.findByUsername(anyString())).thenAnswer(inv -> {
            String u = inv.getArgument(0, String.class);
            if ("admin".equals(u)) {
                User user = new User();
                user.setId(1L);
                user.setUsername("admin");
                user.setRole("admin");
                user.setPassword("enc:admin123");
                user.setCreateTime(LocalDateTime.now());
                return user;
            }
            return null;
        });
        when(userService.createUser(anyString(), anyString())).thenAnswer(inv -> {
            User u = new User();
            u.setId(2L);
            u.setUsername(inv.getArgument(0, String.class));
            u.setRole("user");
            u.setPassword("enc:" + inv.getArgument(1, String.class));
            u.setCreateTime(LocalDateTime.now());
            return u;
        });
    }

    private static RequestBuilder buildRequest(ApiCase c, String path) throws Exception {
        Map<String, String> params = new LinkedHashMap<>(c.requiredParams());
        if ("/api/pipeline/file".equals(path)) {
            params.put("key", "test");
        }
        if ("/api/user/favorites".equals(path) && "DELETE".equals(c.httpMethod())) {
            params.put("sourceTable", "job_info");
            params.put("jobUrl", "https://example.com/job/1");
        }

        if (c.multipart()) {
            MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
            MockMultipartHttpServletRequestBuilder mp = multipart(path).file(file);
            mp.with(r -> {
                r.setMethod(c.httpMethod());
                return r;
            });
            params.forEach(mp::param);
            applyPrincipalIfNeeded(mp, path);
            return mp;
        }

        var builder = switch (c.httpMethod()) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            case "PATCH" -> patch(path);
            default -> null;
        };
        if (builder == null) return get(path);

        params.forEach(builder::param);

        if (c.requestBodyType() != null) {
            Object bodyObj = buildSampleBody(c.requestBodyType());
            if ("/api/auth/login".equals(path) || "/api/auth/register".equals(path)) {
                bodyObj = Map.of("username", "admin", "password", "admin123");
            }
            builder.contentType(MediaType.APPLICATION_JSON);
            builder.content(objectMapper.writeValueAsBytes(bodyObj));
        }

        if (c.produces() != null && c.produces().contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            builder.accept(MediaType.TEXT_EVENT_STREAM);
        }

        applyPrincipalIfNeeded(builder, path);
        return builder;
    }

    private static void applyPrincipalIfNeeded(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String path) {
        if (path == null) return;
        if (!path.startsWith("/api/")) return;
        if (path.startsWith("/api/auth/")) return;
        if (path.equals("/api/resume/parse")) return;
        builder.principal(adminAuth());
    }

    private static Object buildSampleBody(Class<?> bodyType) {
        if (bodyType == null) return null;
        if (Map.class.isAssignableFrom(bodyType)) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sourceTable", "job_info");
            out.put("jobUrl", "https://example.com/job/1");
            out.put("job", Map.of("id", 1, "jobUrl", "https://example.com/job/1"));
            out.put("profile", Map.of("name", "test"));
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field f : bodyType.getDeclaredFields()) {
            out.put(f.getName(), sampleJsonValue(f.getType()));
        }
        return out;
    }

    private static Object sampleJsonValue(Class<?> t) {
        if (t == null) return null;
        if (t == String.class) return "test";
        if (t == Integer.class || t == int.class) return 1;
        if (t == Long.class || t == long.class) return 1L;
        if (t == Double.class || t == double.class) return 1.0;
        if (t == Boolean.class || t == boolean.class) return true;
        if (Map.class.isAssignableFrom(t)) return Map.of();
        if (List.class.isAssignableFrom(t)) return List.of();
        return null;
    }

    private static String fillPathVariables(String pattern) {
        if (pattern == null) return "";
        return pattern.replaceAll("\\{[^/]+}", "1");
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) return "";
        int i = contentType.indexOf(';');
        return i >= 0 ? contentType.substring(0, i).trim() : contentType.trim();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static List<ApiCase> collectCases(List<Class<?>> controllers) {
        List<ApiCase> out = new ArrayList<>();
        for (Class<?> c : controllers) {
            RequestMapping base = c.getAnnotation(RequestMapping.class);
            List<String> basePaths = extractPaths(base);
            if (basePaths.isEmpty()) basePaths = List.of("");

            for (Method m : c.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;

                List<ApiCase> methodCases = extractMethodCases(m, basePaths);
                out.addAll(methodCases);
            }
        }
        out.removeIf(a -> !a.pathTemplate().startsWith("/api/"));
        out.sort(Comparator.comparing(ApiCase::pathTemplate).thenComparing(ApiCase::httpMethod));
        return out;
    }

    private static List<ApiCase> extractMethodCases(Method m, List<String> basePaths) {
        List<ApiCase> cases = new ArrayList<>();

        GetMapping gm = m.getAnnotation(GetMapping.class);
        PostMapping pm = m.getAnnotation(PostMapping.class);
        PutMapping pum = m.getAnnotation(PutMapping.class);
        DeleteMapping dm = m.getAnnotation(DeleteMapping.class);
        PatchMapping pam = m.getAnnotation(PatchMapping.class);
        RequestMapping rm = m.getAnnotation(RequestMapping.class);

        if (gm != null) cases.addAll(buildCases("GET", basePaths, extractPaths(gm), m, gm.produces()));
        if (pm != null) cases.addAll(buildCases("POST", basePaths, extractPaths(pm), m, pm.produces()));
        if (pum != null) cases.addAll(buildCases("PUT", basePaths, extractPaths(pum), m, pum.produces()));
        if (dm != null) cases.addAll(buildCases("DELETE", basePaths, extractPaths(dm), m, dm.produces()));
        if (pam != null) cases.addAll(buildCases("PATCH", basePaths, extractPaths(pam), m, pam.produces()));

        if (rm != null) {
            List<String> paths = extractPaths(rm);
            RequestMethod[] ms = rm.method();
            if (ms == null || ms.length == 0) {
                cases.addAll(buildCases("GET", basePaths, paths, m, rm.produces()));
            } else {
                for (RequestMethod r : ms) {
                    cases.addAll(buildCases(r.name(), basePaths, paths, m, rm.produces()));
                }
            }
        }

        return cases;
    }

    private static List<ApiCase> buildCases(String httpMethod, List<String> basePaths, List<String> methodPaths, Method handler, String[] producesArr) {
        List<ApiCase> out = new ArrayList<>();
        List<String> mp = methodPaths == null || methodPaths.isEmpty() ? List.of("") : methodPaths;
        String produces = producesArr != null && producesArr.length > 0 ? String.join(",", producesArr) : "";

        boolean multipart = false;
        Class<?> bodyType = null;
        Map<String, String> requiredParams = new LinkedHashMap<>();

        for (Parameter p : handler.getParameters()) {
            if (MultipartFile.class.isAssignableFrom(p.getType())) multipart = true;
            if (p.getAnnotation(RequestBody.class) != null) bodyType = p.getType();

            RequestParam rp = p.getAnnotation(RequestParam.class);
            if (rp != null) {
                String name = (rp.name() != null && !rp.name().isBlank()) ? rp.name() : rp.value();
                boolean required = rp.required();
                boolean hasDefault = rp.defaultValue() != null && !rp.defaultValue().equals(ValueConstants.DEFAULT_NONE);
                if (required && !hasDefault && name != null && !name.isBlank()) {
                    requiredParams.put(name, sampleValueForType(p.getType()));
                }
            }
        }

        for (String bp : basePaths) {
            for (String p : mp) {
                String full = normalizePath(bp, p);
                out.add(new ApiCase(httpMethod, full, handler, multipart, bodyType, requiredParams, produces));
            }
        }
        return out;
    }

    private static String normalizePath(String base, String child) {
        String b = base == null ? "" : base.trim();
        String c = child == null ? "" : child.trim();
        if (!b.startsWith("/")) b = "/" + b;
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (c.isEmpty() || "/".equals(c)) return b.isEmpty() ? "/" : b;
        if (!c.startsWith("/")) c = "/" + c;
        return (b + c).replaceAll("//+", "/");
    }

    private static List<String> extractPaths(RequestMapping rm) {
        if (rm == null) return List.of();
        if (rm.path().length > 0) return Arrays.asList(rm.path());
        if (rm.value().length > 0) return Arrays.asList(rm.value());
        return List.of();
    }

    private static List<String> extractPaths(GetMapping m) {
        if (m == null) return List.of();
        if (m.path().length > 0) return Arrays.asList(m.path());
        if (m.value().length > 0) return Arrays.asList(m.value());
        return List.of();
    }

    private static List<String> extractPaths(PostMapping m) {
        if (m == null) return List.of();
        if (m.path().length > 0) return Arrays.asList(m.path());
        if (m.value().length > 0) return Arrays.asList(m.value());
        return List.of();
    }

    private static List<String> extractPaths(PutMapping m) {
        if (m == null) return List.of();
        if (m.path().length > 0) return Arrays.asList(m.path());
        if (m.value().length > 0) return Arrays.asList(m.value());
        return List.of();
    }

    private static List<String> extractPaths(DeleteMapping m) {
        if (m == null) return List.of();
        if (m.path().length > 0) return Arrays.asList(m.path());
        if (m.value().length > 0) return Arrays.asList(m.value());
        return List.of();
    }

    private static List<String> extractPaths(PatchMapping m) {
        if (m == null) return List.of();
        if (m.path().length > 0) return Arrays.asList(m.path());
        if (m.value().length > 0) return Arrays.asList(m.value());
        return List.of();
    }

    private static String sampleValueForType(Class<?> t) {
        if (t == null) return "1";
        if (t == Boolean.class || t == boolean.class) return "true";
        if (Number.class.isAssignableFrom(t) || t.isPrimitive()) return "1";
        return "test";
    }

    private static void writeReport(List<Map<String, Object>> results, long okCount, long total) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# API 自动化测试报告\n\n");
        sb.append("- 总用例数: ").append(total).append('\n');
        sb.append("- 通过: ").append(okCount).append('\n');
        sb.append("- 失败: ").append(total - okCount).append('\n');
        sb.append("- 生成时间: ").append(LocalDateTime.now()).append("\n\n");
        sb.append("| Method | Path | Status | Content-Type | OK | Latency(ms) | Handler |\n");
        sb.append("|---|---|---:|---|---|---:|---|\n");
        for (var r : results) {
            sb.append('|').append(r.get("method")).append(' ');
            sb.append('|').append(r.get("path")).append(' ');
            sb.append('|').append(r.get("status")).append(' ');
            sb.append('|').append(r.get("contentType")).append(' ');
            sb.append('|').append(Boolean.TRUE.equals(r.get("ok")) ? "PASS" : "FAIL").append(' ');
            sb.append('|').append(r.get("latencyMs")).append(' ');
            sb.append('|').append(r.get("handler")).append(" |\n");
        }

        sb.append("\n## 失败详情\n\n");
        results.stream().filter(m -> !Boolean.TRUE.equals(m.get("ok"))).forEach(m -> {
            sb.append("- ").append(m.get("method")).append(' ').append(m.get("path")).append(" (")
                    .append(m.get("status")).append(") ").append(m.get("handler")).append('\n');
            Object preview = m.get("bodyPreview");
            if (preview != null && !String.valueOf(preview).isBlank()) {
                sb.append("  - body: ").append(String.valueOf(preview)).append('\n');
            }
        });

        Path outDir = Path.of("test-report");
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("api-test-report.md"), sb.toString(), StandardCharsets.UTF_8);
        Files.writeString(outDir.resolve("api-test-report.json"), objectMapper.writeValueAsString(results), StandardCharsets.UTF_8);
    }
}
