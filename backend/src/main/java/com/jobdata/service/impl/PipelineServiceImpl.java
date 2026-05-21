package com.jobdata.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.service.PipelineService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PipelineServiceImpl implements PipelineService {

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

    private final Deque<Map<String, String>> logs = new ArrayDeque<>();
    private final Object lock = new Object();

    @Override
    public Map<String, Object> startDashboardPipeline() {
        Map<String, Object> result = new HashMap<>();
        synchronized (lock) {
            if ("running".equals(status)) {
                result.put("success", false);
                result.put("message", "Pipeline 正在运行中");
                return result;
            }
            status = "running";
            lastStartTime = LocalDateTime.now();
            lastEndTime = null;
            lastExitCode = null;
            lastMessage = "Pipeline 已启动";
            lastRunDir = null;
            lastArtifacts = new LinkedHashMap<>();
            lastSummary = new LinkedHashMap<>();
            lastErrors = new ArrayList<>();
            synchronized (logs) {
                logs.clear();
            }

            new Thread(() -> {
                try {
                    runDashboard();
                    status = "idle";
                    lastMessage = "Pipeline 运行完成";
                } catch (Exception e) {
                    status = "failed";
                    lastMessage = "Pipeline 运行失败: " + e.getMessage();
                    addLog("[ERROR] " + e.getMessage());
                } finally {
                    lastEndTime = LocalDateTime.now();
                    currentProcess = null;
                }
            }, "pipeline-dashboard-runner").start();

            result.put("success", true);
            result.put("message", "Pipeline 已启动");
            result.put("status", status);
            return result;
        }
    }

    @Override
    public Map<String, Object> getPipelineStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("lastStartTime", lastStartTime == null ? null : lastStartTime.toString());
        result.put("lastEndTime", lastEndTime == null ? null : lastEndTime.toString());
        result.put("lastExitCode", lastExitCode);
        result.put("message", lastMessage);
        result.put("runDir", lastRunDir);
        synchronized (logs) {
            result.put("logs", new ArrayList<>(logs));
        }
        return result;
    }

    @Override
    public Map<String, Object> getPipelineArtifacts() {
        Map<String, Object> result = new HashMap<>();
        result.put("runDir", lastRunDir);
        result.put("artifacts", lastArtifacts);
        result.put("summary", lastSummary);
        result.put("errors", lastErrors);
        return result;
    }

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
            if (lastRunDir != null && !lastRunDir.trim().isEmpty()) {
                Path base = Paths.get(lastRunDir).toAbsolutePath().normalize();
                if (!p.startsWith(base)) {
                    return null;
                }
            }
            File f = p.toFile();
            return f.exists() ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

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

        ObjectMapper mapper = new ObjectMapper();
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
            summary.put("cluster_ready", artifacts.get("cluster_scatter_png") != null);
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
