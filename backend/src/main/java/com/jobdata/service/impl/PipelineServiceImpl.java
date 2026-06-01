package com.jobdata.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.mapper.JobInfo51JobMapper;
import com.jobdata.mapper.JobInfoMapper;
import com.jobdata.service.PipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pipeline 服务实现：负责执行 crawler/nlp_job_pipeline.py，并提供状态、日志与产物查询。
 */
@Service
public class PipelineServiceImpl implements PipelineService {
    private static final long DEFAULT_DASHBOARD_ESTIMATE_SECONDS = 120;
    private static final long DEFAULT_STATS_ESTIMATE_SECONDS = 30;

    private static final String PYTHON_CMD = "python";
    private static final int MAX_LOG_LINES = 500;
    private static final String PIPELINE_MARKER = "__PIPELINE_JSON__";

    private volatile String status = "idle";
    private volatile LocalDateTime lastStartTime = null;
    private volatile LocalDateTime lastEndTime = null;
    private volatile Integer lastExitCode = null;
    private volatile String lastMessage = "暂无运行记录";

    private volatile Process currentProcess = null;
    private volatile String lastRunDir = null;
    private volatile Map<String, String> lastArtifacts = new LinkedHashMap<>();
    private volatile Map<String, Object> lastSummary = new LinkedHashMap<>();
    private volatile List<String> lastErrors = new ArrayList<>();
    private volatile Map<String, Object> lastFingerprint = new LinkedHashMap<>();
    private volatile Boolean lastCached = false;
    private volatile String currentKind = null;
    private volatile Long lastDashboardDurationSeconds = null;
    private volatile Long lastStatsDurationSeconds = null;

    private final Deque<Map<String, String>> logs = new ArrayDeque<>();
    private final Object lock = new Object();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private JobInfoMapper jobInfoMapper;

    @Autowired
    private JobInfo51JobMapper jobInfo51JobMapper;

    /**
     * 启动 dashboard pipeline（非强制）。
     *
     * @return 启动结果
     */
    @Override
    public Map<String, Object> startDashboardPipeline() {
        return startDashboardPipeline(false);
    }

    /**
     * 启动 dashboard pipeline。
     *
     * @param force 是否强制重算（忽略缓存）
     * @return 启动结果
     */
    @Override
    public Map<String, Object> startDashboardPipeline(boolean force) {
        Map<String, Object> result = new HashMap<>();
        synchronized (lock) {
            if ("running".equals(status)) {
                result.put("success", false);
                result.put("message", "Pipeline 正在运行中");
                return result;
            }

            Map<String, Object> fingerprint = safeGetFingerprint();
            CachePayload cache = loadCacheIfPresent();
            if (!force && fingerprint != null && cache != null && cache.fingerprint != null
                    && mapper.valueToTree(cache.fingerprint).equals(mapper.valueToTree(fingerprint))
                    && cache.runDir != null && cache.artifacts != null
                    && artifactsLookUsable(cache.runDir, cache.artifacts)) {
                status = "idle";
                currentKind = "dashboard";
                lastStartTime = LocalDateTime.now();
                lastEndTime = LocalDateTime.now();
                lastExitCode = 0;
                lastMessage = "已使用缓存结果（数据未变化）";
                lastRunDir = cache.runDir;
                lastArtifacts = new LinkedHashMap<>(cache.artifacts);
                lastErrors = cache.errors == null ? new ArrayList<>() : new ArrayList<>(cache.errors);
                lastSummary = buildSummary(lastArtifacts, lastErrors);
                lastFingerprint = new LinkedHashMap<>(fingerprint);
                lastCached = true;
                synchronized (logs) {
                    logs.clear();
                    addLog("[Cache] 命中缓存，跳过聚类/训练");
                }
                result.put("success", true);
                result.put("cached", true);
                result.put("message", lastMessage);
                result.put("status", status);
                result.put("runDir", lastRunDir);
                return result;
            }

            status = "running";
            currentKind = "dashboard";
            lastStartTime = LocalDateTime.now();
            lastEndTime = null;
            lastExitCode = null;
            lastMessage = force ? "Pipeline 已启动（强制重算）" : "Pipeline 已启动";
            lastRunDir = null;
            lastArtifacts = new LinkedHashMap<>();
            lastSummary = new LinkedHashMap<>();
            lastErrors = new ArrayList<>();
            lastFingerprint = fingerprint == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fingerprint);
            lastCached = false;
            synchronized (logs) {
                logs.clear();
            }

            final Map<String, Object> fingerprintForSave = fingerprint;
            new Thread(() -> {
                try {
                    runDashboard();
                    status = "idle";
                    lastMessage = "Pipeline 运行完成";
                    if (fingerprintForSave != null && lastRunDir != null && lastArtifacts != null) {
                        saveCache(new CachePayload(
                                lastRunDir,
                                new LinkedHashMap<>(lastArtifacts),
                                new ArrayList<>(lastErrors),
                                new LinkedHashMap<>(fingerprintForSave),
                                LocalDateTime.now().toString()
                        ));
                    }
                } catch (Exception e) {
                    status = "failed";
                    lastMessage = "Pipeline 运行失败: " + e.getMessage();
                    addLog("[ERROR] " + e.getMessage());
                } finally {
                    lastEndTime = LocalDateTime.now();
                    if (Boolean.FALSE.equals(lastCached) && lastExitCode != null && lastExitCode == 0 && lastStartTime != null) {
                        long d = Duration.between(lastStartTime, lastEndTime).getSeconds();
                        if (d > 0) {
                            lastDashboardDurationSeconds = d;
                        }
                    }
                    currentProcess = null;
                }
            }, "pipeline-dashboard-runner").start();

            result.put("success", true);
            result.put("message", "Pipeline 已启动");
            result.put("status", status);
            result.put("cached", false);
            return result;
        }
    }

    /**
     * 启动 stats pipeline（非强制）。
     *
     * @return 启动结果
     */
    @Override
    public Map<String, Object> startStatsPipeline() {
        return startStatsPipeline(false);
    }

    /**
     * 启动 stats pipeline。
     *
     * @param force 是否强制重算（忽略缓存）
     * @return 启动结果
     */
    @Override
    public Map<String, Object> startStatsPipeline(boolean force) {
        Map<String, Object> result = new HashMap<>();
        synchronized (lock) {
            if ("running".equals(status)) {
                result.put("success", false);
                result.put("message", "Pipeline 正在运行中");
                return result;
            }

            Map<String, Object> fingerprint = safeGetFingerprint();
            if (!force && fingerprint != null && lastFingerprint != null
                    && mapper.valueToTree(lastFingerprint).equals(mapper.valueToTree(fingerprint))
                    && lastArtifacts != null && artifactExists(lastArtifacts.get("top_tokens"))) {
                status = "idle";
                currentKind = "stats";
                lastStartTime = LocalDateTime.now();
                lastEndTime = LocalDateTime.now();
                lastExitCode = 0;
                lastMessage = "已使用缓存 Top Tokens（数据未变化）";
                lastFingerprint = new LinkedHashMap<>(fingerprint);
                lastCached = true;
                lastSummary = buildSummary(lastArtifacts, lastErrors);
                synchronized (logs) {
                    logs.clear();
                    addLog("[Cache] 命中缓存，跳过 stats");
                }
                result.put("success", true);
                result.put("cached", true);
                result.put("message", lastMessage);
                result.put("status", status);
                result.put("runDir", lastRunDir);
                return result;
            }

            status = "running";
            currentKind = "stats";
            lastStartTime = LocalDateTime.now();
            lastEndTime = null;
            lastExitCode = null;
            lastMessage = force ? "Stats 已启动（强制重算）" : "Stats 已启动";
            lastFingerprint = fingerprint == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fingerprint);
            lastCached = false;
            synchronized (logs) {
                logs.clear();
            }

            new Thread(() -> {
                try {
                    runStats();
                    status = "idle";
                    lastMessage = "Stats 运行完成";
                } catch (Exception e) {
                    status = "failed";
                    lastMessage = "Stats 运行失败: " + e.getMessage();
                    addLog("[ERROR] " + e.getMessage());
                } finally {
                    lastEndTime = LocalDateTime.now();
                    if (Boolean.FALSE.equals(lastCached) && lastExitCode != null && lastExitCode == 0 && lastStartTime != null) {
                        long d = Duration.between(lastStartTime, lastEndTime).getSeconds();
                        if (d > 0) {
                            lastStatsDurationSeconds = d;
                        }
                    }
                    currentProcess = null;
                }
            }, "pipeline-stats-runner").start();

            result.put("success", true);
            result.put("message", "Stats 已启动");
            result.put("status", status);
            result.put("cached", false);
            return result;
        }
    }

    /**
     * 获取 pipeline 当前状态与日志。
     *
     * @return 状态信息
     */
    @Override
    public Map<String, Object> getPipelineStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("lastStartTime", lastStartTime == null ? null : lastStartTime.toString());
        result.put("lastEndTime", lastEndTime == null ? null : lastEndTime.toString());
        result.put("lastExitCode", lastExitCode);
        result.put("message", lastMessage);
        result.put("runDir", lastRunDir);
        result.put("kind", currentKind);
        result.put("serverNowMs", System.currentTimeMillis());

        Long estimate = null;
        if ("running".equals(status)) {
            if ("dashboard".equals(currentKind)) {
                estimate = lastDashboardDurationSeconds;
            } else if ("stats".equals(currentKind)) {
                estimate = lastStatsDurationSeconds;
            }
            if (estimate == null) {
                estimate = "stats".equals(currentKind) ? DEFAULT_STATS_ESTIMATE_SECONDS : DEFAULT_DASHBOARD_ESTIMATE_SECONDS;
            }
        }
        result.put("estimatedTotalSeconds", estimate);
        if (estimate != null && lastStartTime != null) {
            ZonedDateTime zdt = lastStartTime.atZone(ZoneId.systemDefault());
            long endMs = zdt.toInstant().toEpochMilli() + estimate * 1000L;
            result.put("estimatedEndMs", endMs);
        } else {
            result.put("estimatedEndMs", null);
        }
        synchronized (logs) {
            result.put("logs", new ArrayList<>(logs));
        }
        return result;
    }

    /**
     * 获取 pipeline 上次执行产物与摘要信息。
     *
     * @return 产物信息
     */
    @Override
    public Map<String, Object> getPipelineArtifacts() {
        Map<String, Object> result = new HashMap<>();
        result.put("runDir", lastRunDir);
        result.put("artifacts", lastArtifacts);
        result.put("summary", lastSummary);
        result.put("errors", lastErrors);
        result.put("fingerprint", lastFingerprint);
        result.put("cached", lastCached);
        return result;
    }

    /**
     * 获取指定 key 对应的产物文件（带目录安全校验，仅允许 output 目录下文件）。
     *
     * @param key 产物 key
     * @return 文件（不存在返回 null）
     */
    @Override
    public File getArtifactFile(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        Map<String, String> artifacts = lastArtifacts;
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        String path = artifacts.get(key);
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        try {
            Path p = Paths.get(path).toAbsolutePath().normalize();
            Path outputRoot = resolveCrawlerDir().resolve("output").toAbsolutePath().normalize();
            if (!p.startsWith(outputRoot)) {
                return null;
            }
            File f = p.toFile();
            return f.exists() ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写入一行 pipeline 日志并控制最大行数。
     *
     * @param line 日志内容
     */
    private void addLog(String line) {
        Map<String, String> item = new HashMap<>();
        item.put("time", LocalDateTime.now().toString());
        item.put("line", line == null ? "" : line);
        synchronized (logs) {
            logs.addLast(item);
            while (logs.size() > MAX_LOG_LINES) {
                logs.removeFirst();
            }
        }
    }

    /**
     * 执行 dashboard pipeline，并解析脚本输出的 JSON 结果（包含 run_dir 与 artifacts）。
     */
    private void runDashboard() throws Exception {
        Path crawlerDir = resolveCrawlerDir();
        Path scriptPath = crawlerDir.resolve("nlp_job_pipeline.py").toAbsolutePath().normalize();
        File scriptFile = scriptPath.toFile();
        if (!scriptFile.exists()) {
            throw new RuntimeException("Pipeline 脚本不存在: " + scriptPath);
        }

        String python = resolvePythonExecutable(crawlerDir);
        addLog("Python: " + python);
        ensurePipelineDependencies(crawlerDir, python);

        ProcessBuilder pb = new ProcessBuilder(
                python,
                "-u",
                scriptPath.toString(),
                "dashboard",
                "--mlp-epochs",
                "3",
                "--textcnn-epochs",
                "3"
        );
        pb.directory(crawlerDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        currentProcess = process;

        String pipelineJsonLine = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            addLog(line);
            if (line.startsWith(PIPELINE_MARKER)) {
                pipelineJsonLine = line.substring(PIPELINE_MARKER.length());
            }
        }

        int exitCode = process.waitFor();
        lastExitCode = exitCode;
        addLog("进程退出码: " + exitCode);
        if (exitCode != 0) {
            throw new RuntimeException("Pipeline 执行失败，退出码: " + exitCode);
        }

        if (pipelineJsonLine == null || pipelineJsonLine.trim().isEmpty()) {
            throw new RuntimeException("Pipeline 未输出结果 JSON");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(pipelineJsonLine, Map.class);

        Object runDir = payload.get("run_dir");
        if (runDir != null) {
            lastRunDir = String.valueOf(runDir);
        }

        Map<String, String> artifacts = new LinkedHashMap<>();
        Object artifactsObj = payload.get("artifacts");
        if (artifactsObj instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) artifactsObj;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (k != null && v != null) {
                    artifacts.put(k, String.valueOf(v));
                }
            }
        }
        lastArtifacts = artifacts;
        lastErrors = readErrors(payload);
        lastSummary = buildSummary(artifacts, lastErrors);
    }

    /**
     * 执行 stats pipeline（生成 top tokens 等统计产物）。
     */
    private void runStats() throws Exception {
        Path crawlerDir = resolveCrawlerDir();
        Path scriptPath = crawlerDir.resolve("nlp_job_pipeline.py").toAbsolutePath().normalize();
        File scriptFile = scriptPath.toFile();
        if (!scriptFile.exists()) {
            throw new RuntimeException("Pipeline 脚本不存在: " + scriptPath);
        }

        String python = resolvePythonExecutable(crawlerDir);
        addLog("Python: " + python);
        ensurePythonModulesInstalled(crawlerDir, python, Arrays.asList("pymysql", "pandas", "jieba"));

        ProcessBuilder pb = new ProcessBuilder(
                python,
                "-u",
                scriptPath.toString(),
                "stats"
        );
        pb.directory(crawlerDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        currentProcess = process;

        String pipelineJsonLine = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            addLog(line);
            if (line.startsWith(PIPELINE_MARKER)) {
                pipelineJsonLine = line.substring(PIPELINE_MARKER.length());
            }
        }

        int exitCode = process.waitFor();
        lastExitCode = exitCode;
        addLog("进程退出码: " + exitCode);
        if (exitCode != 0) {
            throw new RuntimeException("Stats 执行失败，退出码: " + exitCode);
        }

        if (pipelineJsonLine == null || pipelineJsonLine.trim().isEmpty()) {
            throw new RuntimeException("Stats 未输出结果 JSON");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = mapper.readValue(pipelineJsonLine, Map.class);

        Object runDir = payload.get("run_dir");
        if (runDir != null) {
            lastRunDir = String.valueOf(runDir);
        }

        Map<String, String> artifactsDelta = new LinkedHashMap<>();
        Object artifactsObj = payload.get("artifacts");
        if (artifactsObj instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) artifactsObj;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                if (k != null && v != null) {
                    artifactsDelta.put(k, String.valueOf(v));
                }
            }
        }

        Map<String, String> merged = new LinkedHashMap<>();
        if (lastArtifacts != null) {
            merged.putAll(lastArtifacts);
        }
        merged.putAll(artifactsDelta);
        lastArtifacts = merged;

        lastErrors = readErrors(payload);
        lastSummary = buildSummary(lastArtifacts, lastErrors);
    }

    private void ensurePythonModulesInstalled(Path workDir, String python, List<String> modules) throws Exception {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String m : modules) {
            if (m == null || m.trim().isEmpty()) {
                continue;
            }
            if (!checkPythonModule(python, workDir, m.trim())) {
                missing.add(m.trim());
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        ensurePipAvailable(workDir, python);
        int exitCode = runPipInstall(workDir, python, missing);
        if (exitCode != 0) {
            throw new RuntimeException("依赖安装失败（" + String.join(",", missing) + "），退出码: " + exitCode);
        }
    }

    private boolean artifactExists(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        try {
            return Paths.get(path).toAbsolutePath().normalize().toFile().exists();
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> safeGetFingerprint() {
        try {
            Map<String, Object> fp = new LinkedHashMap<>();
            Map<String, Object> boss = jobInfoMapper.getFingerprint();
            Map<String, Object> job51 = jobInfo51JobMapper.getFingerprint();
            fp.put("job_info", normalizeFpRow(boss));
            fp.put("job_info_51job", normalizeFpRow(job51));
            return fp;
        } catch (Exception e) {
            addLog("[Fingerprint] 获取失败: " + e.getMessage());
            return null;
        }
    }

    private Map<String, Object> normalizeFpRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row == null) {
            out.put("cnt", 0);
            out.put("maxCreatedAt", null);
            return out;
        }
        Object cnt = row.get("cnt");
        Object max = row.get("maxCreatedAt");
        out.put("cnt", cnt == null ? 0 : Long.parseLong(String.valueOf(cnt)));
        out.put("maxCreatedAt", max == null ? null : String.valueOf(max));
        return out;
    }

    private boolean artifactsLookUsable(String runDir, Map<String, String> artifacts) {
        try {
            if (runDir == null || runDir.trim().isEmpty()) {
                return false;
            }
            Path base = Paths.get(runDir).toAbsolutePath().normalize();
            if (!base.toFile().exists()) {
                return false;
            }
            String[] must = new String[]{"jobs_clean_csv", "jobs_reduced_csv", "cluster_scatter_svg"};
            for (String k : must) {
                String p = artifacts.get(k);
                if (p == null || p.trim().isEmpty()) {
                    return false;
                }
                Path fp = Paths.get(p).toAbsolutePath().normalize();
                if (!fp.startsWith(base) || !fp.toFile().exists()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Path resolveCacheFile() {
        Path crawlerDir = resolveCrawlerDir();
        Path outDir = crawlerDir.resolve("output");
        return outDir.resolve("pipeline_cache.json").toAbsolutePath().normalize();
    }

    private CachePayload loadCacheIfPresent() {
        try {
            Path p = resolveCacheFile();
            if (!p.toFile().exists()) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = mapper.readValue(p.toFile(), Map.class);
            String runDir = m.get("runDir") == null ? null : String.valueOf(m.get("runDir"));
            Map<String, String> artifacts = new LinkedHashMap<>();
            Object a = m.get("artifacts");
            if (a instanceof Map) {
                Map<?, ?> mm = (Map<?, ?>) a;
                for (Map.Entry<?, ?> e : mm.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        artifacts.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                }
            }
            List<String> errors = new ArrayList<>();
            Object err = m.get("errors");
            if (err instanceof List) {
                for (Object x : (List<?>) err) {
                    if (x != null && !String.valueOf(x).trim().isEmpty()) {
                        errors.add(String.valueOf(x));
                    }
                }
            }
            Object fp = m.get("fingerprint");
            Map<String, Object> fingerprint = fp instanceof Map ? (Map<String, Object>) fp : null;
            String updatedAt = m.get("updatedAt") == null ? null : String.valueOf(m.get("updatedAt"));
            if (runDir == null || artifacts.isEmpty() || fingerprint == null) {
                return null;
            }
            return new CachePayload(runDir, artifacts, errors, fingerprint, updatedAt);
        } catch (Exception e) {
            addLog("[Cache] 读取失败: " + e.getMessage());
            return null;
        }
    }

    private void saveCache(CachePayload payload) {
        try {
            Path p = resolveCacheFile();
            Files.createDirectories(p.getParent());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("runDir", payload.runDir);
            m.put("artifacts", payload.artifacts);
            m.put("errors", payload.errors);
            m.put("fingerprint", payload.fingerprint);
            m.put("updatedAt", payload.updatedAt);
            mapper.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), m);
            addLog("[Cache] 已写入缓存: " + p);
        } catch (Exception e) {
            addLog("[Cache] 写入失败: " + e.getMessage());
        }
    }

    private static class CachePayload {
        final String runDir;
        final Map<String, String> artifacts;
        final List<String> errors;
        final Map<String, Object> fingerprint;
        final String updatedAt;

        CachePayload(String runDir, Map<String, String> artifacts, List<String> errors, Map<String, Object> fingerprint, String updatedAt) {
            this.runDir = runDir;
            this.artifacts = artifacts;
            this.errors = errors;
            this.fingerprint = fingerprint;
            this.updatedAt = updatedAt;
        }
    }

    private List<String> readErrors(Map<String, Object> payload) {
        try {
            Object errObj = payload.get("errors");
            if (errObj instanceof List) {
                List<?> lst = (List<?>) errObj;
                List<String> out = new ArrayList<>();
                for (Object x : lst) {
                    if (x != null) {
                        String s = String.valueOf(x).trim();
                        if (!s.isEmpty()) {
                            out.add(s);
                        }
                    }
                }
                return out;
            }
            return new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> buildSummary(Map<String, String> artifacts, List<String> errors) {
        Map<String, Object> summary = new LinkedHashMap<>();

        Object mlpAcc = readLastValAcc(artifacts == null ? null : artifacts.get("mlp_history"));
        if (mlpAcc != null) {
            summary.put("mlp_val_acc", mlpAcc);
        }
        Object textcnnAcc = readLastValAcc(artifacts == null ? null : artifacts.get("textcnn_history"));
        if (textcnnAcc != null) {
            summary.put("textcnn_val_acc", textcnnAcc);
        }

        List<Map<String, Object>> topTokens = readTopTokens(artifacts == null ? null : artifacts.get("top_tokens"));
        if (topTokens != null) {
            summary.put("top_tokens", topTokens);
        }

        if (artifacts != null) {
            summary.put("cluster_ready", artifacts.get("cluster_scatter_svg") != null);
            summary.put("mlp_ready", artifacts.get("mlp_history") != null);
            summary.put("textcnn_ready", artifacts.get("textcnn_history") != null);
        }
        if (errors != null && !errors.isEmpty()) {
            summary.put("has_errors", true);
        }
        return summary;
    }

    private Object readLastValAcc(String historyPath) {
        if (historyPath == null || historyPath.trim().isEmpty()) {
            return null;
        }
        try {
            Path p = Paths.get(historyPath).toAbsolutePath().normalize();
            if (!p.toFile().exists()) {
                return null;
            }
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            if (lines.size() < 2) {
                return null;
            }
            String header = lines.get(0).replace("\uFEFF", "");
            int idx = -1;
            String[] cols = header.split(",");
            for (int i = 0; i < cols.length; i++) {
                if ("val_acc".equals(cols[i].trim())) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                return null;
            }
            String last = lines.get(lines.size() - 1);
            String[] parts = last.split(",");
            if (idx >= parts.length) {
                return null;
            }
            return Double.parseDouble(parts[idx].trim());
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> readTopTokens(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        try {
            Path p = Paths.get(path).toAbsolutePath().normalize();
            if (!p.toFile().exists()) {
                return null;
            }
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            if (lines.size() < 2) {
                return null;
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 1; i < Math.min(lines.size(), 11); i++) {
                String[] parts = lines.get(i).split(",");
                if (parts.length < 2) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("token", parts[0]);
                item.put("count", Integer.parseInt(parts[1].trim()));
                out.add(item);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private Path resolveCrawlerDir() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = Arrays.asList(
                current.resolve("crawler"),
                current.resolve("..").resolve("crawler"),
                current.resolve("..").resolve("..").resolve("crawler")
        );
        for (Path candidate : candidates) {
            Path script = candidate.resolve("nlp_job_pipeline.py");
            if (script.toFile().exists()) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return candidates.get(0).toAbsolutePath().normalize();
    }

    private String resolvePythonExecutable(Path crawlerDir) {
        String configured = System.getenv("JOBDATA_PYTHON");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }

        String condaPythonExe = System.getenv("CONDA_PYTHON_EXE");
        if (condaPythonExe != null && !condaPythonExe.trim().isEmpty()) {
            return condaPythonExe.trim();
        }

        String condaPrefix = System.getenv("CONDA_PREFIX");
        if (condaPrefix != null && !condaPrefix.trim().isEmpty()) {
            Path p = Paths.get(condaPrefix.trim()).resolve("python.exe");
            if (p.toFile().exists()) {
                return p.toAbsolutePath().normalize().toString();
            }
        }

        List<Path> candidates = Arrays.asList(
                crawlerDir.resolve(".venv").resolve("Scripts").resolve("python.exe"),
                crawlerDir.resolve("venv").resolve("Scripts").resolve("python.exe"),
                crawlerDir.resolve("env").resolve("Scripts").resolve("python.exe")
        );
        for (Path p : candidates) {
            if (p.toFile().exists()) {
                return p.toAbsolutePath().normalize().toString();
            }
        }
        return PYTHON_CMD;
    }

    private void ensurePipelineDependencies(Path workDir, String python) throws Exception {
        Path requirements = workDir.resolve("requirements.txt");
        if (!requirements.toFile().exists()) {
            return;
        }

        List<String> modules = Arrays.asList("pymysql", "pandas", "jieba", "sklearn", "matplotlib", "torch");
        boolean allOk = true;
        List<String> missing = new ArrayList<>();
        for (String m : modules) {
            if (!checkPythonModule(python, workDir, m)) {
                allOk = false;
                missing.add(m);
            }
        }
        if (allOk) {
            return;
        }

        if (missing.size() == 1 && "torch".equals(missing.get(0))) {
            addLog("[Pip] torch 未安装，先跳过安装（Dashboard 将继续输出聚类/统计结果，深度学习部分会提示缺依赖）");
            return;
        }

        ensurePipAvailable(workDir, python);
        int exitCode = runPipInstall(workDir, python, Arrays.asList("-r", requirements.toString()));
        if (exitCode == 0) {
            return;
        }

        addLog("[Pip] requirements 安装失败，尝试跳过 torch 继续安装其余依赖...");
        List<String> reqLines = Files.readAllLines(requirements, StandardCharsets.UTF_8);
        List<String> filtered = reqLines.stream()
                .map(String::trim)
                .filter(s -> s != null && !s.isEmpty())
                .filter(s -> !s.startsWith("#"))
                .filter(s -> !s.toLowerCase().startsWith("torch"))
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            throw new RuntimeException("依赖安装失败，退出码: " + exitCode);
        }

        int exit2 = runPipInstall(workDir, python, filtered);
        if (exit2 != 0) {
            throw new RuntimeException("依赖安装失败，退出码: " + exit2);
        }
        addLog("[Pip] 已安装除 torch 外的依赖（如需深度学习，请手动安装 torch 或设置可用的 Python 环境）");
    }

    private int runPipInstall(Path workDir, String python, List<String> pipArgs) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(python);
        cmd.add("-m");
        cmd.add("pip");
        cmd.add("install");
        cmd.addAll(pipArgs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("PIP_DISABLE_PIP_VERSION_CHECK", "1");
        pb.environment().put("PIP_NO_INPUT", "1");
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            addLog("[Pip] " + line);
        }
        return process.waitFor();
    }

    private void ensurePipAvailable(Path workDir, String python) throws Exception {
        if (checkPythonModule(python, workDir, "pip")) {
            return;
        }

        ProcessBuilder pb = new ProcessBuilder(python, "-m", "ensurepip", "--upgrade");
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            addLog("[EnsurePip] " + line);
        }

        int exitCode = process.waitFor();
        if (exitCode == 0 && checkPythonModule(python, workDir, "pip")) {
            return;
        }

        throw new RuntimeException("当前 Python 环境缺少 pip，请设置环境变量 JOBDATA_PYTHON 指向 conda 的 python.exe（或创建 crawler/.venv）。当前: " + python);
    }

    private boolean checkPythonModule(String python, Path workDir, String moduleName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(python, "-c", "import " + moduleName);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().close();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
