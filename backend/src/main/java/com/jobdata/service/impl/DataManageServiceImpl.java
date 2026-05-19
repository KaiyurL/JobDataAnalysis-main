package com.jobdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobdata.entity.JobInfo;
import com.jobdata.entity.JobInfo51Job;
import com.jobdata.mapper.JobInfoMapper;
import com.jobdata.mapper.JobInfo51JobMapper;
import com.jobdata.service.DataManageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DataManageServiceImpl implements DataManageService {

    @Autowired
    private JobInfoMapper jobInfoMapper;

    @Autowired
    private JobInfo51JobMapper jobInfo51JobMapper;

    private static final String[] KEYWORDS = {"Java", "Python", "前端", "数据分析", "产品经理"};
    private static final String PYTHON_CMD = "python";

    // 状态: idle, running, failed
    private volatile String crawlerStatus = "idle";
    private volatile LocalDateTime lastStartTime = null;
    private volatile LocalDateTime lastEndTime = null;
    private volatile Integer lastExitCode = null;
    private volatile String lastMessage = "暂无更新记录";
    private volatile boolean waitingForLogin = false;

    private volatile Process currentProcess = null;
    private volatile OutputStream currentStdin = null;

    private final Deque<Map<String, String>> crawlerLogs = new ArrayDeque<>();
    private static final int MAX_LOG_LINES = 300;

    // 防止并发
    private final Object lock = new Object();

    private void addCrawlerLog(String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Map<String, String> item = new HashMap<>();
        item.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        item.put("text", trimmed);
        synchronized (crawlerLogs) {
            crawlerLogs.addLast(item);
            while (crawlerLogs.size() > MAX_LOG_LINES) {
                crawlerLogs.removeFirst();
            }
        }
    }

    private List<Map<String, String>> snapshotLogs() {
        synchronized (crawlerLogs) {
            return new ArrayList<>(crawlerLogs);
        }
    }

    @Override
    public Map<String, Object> getDataOverview() {
        Map<String, Object> result = new HashMap<>();

        // 总记录数
        Long totalBoss = jobInfoMapper.selectCount(null);
        Long total51 = jobInfo51JobMapper.selectCount(null);
        result.put("totalCountBoss", totalBoss);
        result.put("totalCount51Job", total51);
        result.put("totalCount", (totalBoss == null ? 0 : totalBoss) + (total51 == null ? 0 : total51));

        // 最后更新时间: 用最新的 createdAt 代替 crawl_date
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(JobInfo::getCreatedAt);
        wrapper.last("LIMIT 1");
        JobInfo latest = jobInfoMapper.selectOne(wrapper);

        LambdaQueryWrapper<JobInfo51Job> wrapper51 = new LambdaQueryWrapper<>();
        wrapper51.orderByDesc(JobInfo51Job::getCreatedAt);
        wrapper51.last("LIMIT 1");
        JobInfo51Job latest51 = jobInfo51JobMapper.selectOne(wrapper51);

        String lastCrawlTime = "未知";
        LocalDateTime last = null;
        if (latest != null && latest.getCreatedAt() != null) {
            last = latest.getCreatedAt();
        }
        if (latest51 != null && latest51.getCreatedAt() != null) {
            if (last == null || latest51.getCreatedAt().isAfter(last)) {
                last = latest51.getCreatedAt();
            }
        }
        if (last != null) {
            lastCrawlTime = last.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        result.put("lastCrawlTime", lastCrawlTime);

        // 关键词分布统计
        Map<String, Integer> keywordCounts = new HashMap<>();
        for (String keyword : KEYWORDS) {
            LambdaQueryWrapper<JobInfo> kwWrapper = new LambdaQueryWrapper<>();
            kwWrapper.like(JobInfo::getJobName, keyword);
            keywordCounts.put(keyword, Math.toIntExact(jobInfoMapper.selectCount(kwWrapper)));
        }
        result.put("keywordCounts", keywordCounts);

        // 状态
        result.put("status", crawlerStatus);
        result.put("lastMessage", lastMessage);
        result.put("waitingForLogin", waitingForLogin);
        if (lastStartTime != null) {
            result.put("lastStartTime", lastStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (lastEndTime != null) {
            result.put("lastEndTime", lastEndTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (lastExitCode != null) {
            result.put("lastExitCode", lastExitCode);
        }
        result.put("logs", snapshotLogs());

        return result;
    }

    @Override
    public Map<String, Object> startUpdate() {
        Map<String, Object> result = new HashMap<>();

        synchronized (lock) {
            if ("running".equals(crawlerStatus)) {
                result.put("success", false);
                result.put("message", "爬虫正在运行中，请稍后再试");
                return result;
            }

            crawlerStatus = "running";
            lastStartTime = LocalDateTime.now();
            lastEndTime = null;
            lastExitCode = null;
            lastMessage = "更新任务已启动...";
            waitingForLogin = false;
            synchronized (crawlerLogs) {
                crawlerLogs.clear();
            }
            addCrawlerLog("更新任务已启动");
        }

        // 异步执行爬虫
        new Thread(() -> {
            try {
                runSpider();
                crawlerStatus = "idle";
                lastEndTime = LocalDateTime.now();
                lastMessage = "上次更新成功 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                addCrawlerLog("爬取结束：成功");
            } catch (Exception e) {
                crawlerStatus = "failed";
                lastEndTime = LocalDateTime.now();
                lastMessage = "更新失败: " + e.getMessage();
                addCrawlerLog("爬取结束：失败 - " + e.getMessage());
                e.printStackTrace();
            }
        }).start();

        result.put("success", true);
        result.put("message", "更新任务已启动，请稍后查看结果");
        return result;
    }

    @Override
    public Map<String, Object> clearLogs() {
        Map<String, Object> result = new HashMap<>();
        synchronized (crawlerLogs) {
            crawlerLogs.clear();
        }
        result.put("success", true);
        result.put("message", "日志已清空");
        return result;
    }

    private Map<String, Object> readCrawlerConfig(Path crawlerDir) {
        try {
            Path configPath = crawlerDir.resolve("config.json");
            if (!configPath.toFile().exists()) {
                return null;
            }
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> cfg = mapper.readValue(configPath.toFile(), Map.class);
            return cfg;
        } catch (Exception e) {
            return null;
        }
    }

    private void runSpider() throws Exception {
        Path crawlerDir = resolveCrawlerDir();
        Path spiderPath = crawlerDir.resolve("spider.py").toAbsolutePath().normalize();
        File spiderFile = spiderPath.toFile();
        if (!spiderFile.exists()) {
            throw new RuntimeException("爬虫脚本不存在: " + spiderPath);
        }

        Map<String, Object> cfg = readCrawlerConfig(crawlerDir);
        if (cfg != null) {
            Object platform = cfg.get("platform");
            Object keywords = cfg.get("keywords");
            Object cities = cfg.get("cities");
            addCrawlerLog("配置 - 数据源: " + (platform == null ? "-" : String.valueOf(platform)));
            addCrawlerLog("配置 - 关键词: " + (keywords == null ? "-" : String.valueOf(keywords)));
            addCrawlerLog("配置 - 城市: " + (cities == null ? "-" : String.valueOf(cities)));
        }

        String python = resolvePythonExecutable(crawlerDir);
        addCrawlerLog("Python: " + python);
        ensureCrawlerDependencies(crawlerDir, python);

        ProcessBuilder pb = new ProcessBuilder(python, spiderPath.toString());
        pb.directory(crawlerDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        currentProcess = process;
        currentStdin = process.getOutputStream();
        waitingForLogin = false;

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            addCrawlerLog(line);
            if (line.contains("按回车") || line.contains("回车键继续")) {
                waitingForLogin = true;
                lastMessage = "等待登录/验证完成，点击“我已登录，继续爬取”";
            }
            if (line.contains("开始爬取")) {
                waitingForLogin = false;
                lastMessage = "爬虫开始抓取数据...";
            }
        }

        int exitCode = process.waitFor();
        lastExitCode = exitCode;
        addCrawlerLog("进程退出码: " + exitCode);
        currentProcess = null;
        currentStdin = null;
        waitingForLogin = false;
        if (exitCode != 0) {
            throw new RuntimeException("爬虫执行失败，退出码: " + exitCode);
        }
    }

    @Override
    public Map<String, Object> confirmLogin() {
        Map<String, Object> result = new HashMap<>();
        synchronized (lock) {
            if (!"running".equals(crawlerStatus) || currentProcess == null || currentStdin == null) {
                result.put("success", false);
                result.put("message", "当前没有正在运行的爬虫进程");
                return result;
            }
            try {
                currentStdin.write("\n".getBytes(StandardCharsets.UTF_8));
                currentStdin.flush();
                waitingForLogin = false;
                lastMessage = "已发送继续信号，爬虫将开始抓取...";
                result.put("success", true);
                result.put("message", "已发送继续信号");
                return result;
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", "发送继续信号失败: " + e.getMessage());
                return result;
            }
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
            Path spider = candidate.resolve("spider.py");
            if (spider.toFile().exists()) {
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

    private void ensureCrawlerDependencies(Path crawlerDir, String python) throws Exception {
        Path requirements = crawlerDir.resolve("requirements.txt");
        if (!requirements.toFile().exists()) {
            return;
        }

        if (checkPythonModule(python, crawlerDir, "pymysql")) {
            return;
        }

        ensurePipAvailable(crawlerDir, python);

        ProcessBuilder pb = new ProcessBuilder(python, "-m", "pip", "install", "-r", requirements.toString());
        pb.directory(crawlerDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            addCrawlerLog("[Pip] " + line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("依赖安装失败，退出码: " + exitCode);
        }
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
            addCrawlerLog("[EnsurePip] " + line);
        }

        int exitCode = process.waitFor();
        if (exitCode == 0 && checkPythonModule(python, workDir, "pip")) {
            return;
        }

        throw new RuntimeException("当前 Python 环境缺少 pip，请在启动后端前设置环境变量 JOBDATA_PYTHON 指向 conda 的 python.exe（或创建 crawler/.venv）。当前: " + python);
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
