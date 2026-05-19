package com.jobdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobdata.entity.JobInfo;
import com.jobdata.mapper.JobInfoMapper;
import com.jobdata.service.DataManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DataManageServiceImpl implements DataManageService {

    @Autowired
    private JobInfoMapper jobInfoMapper;

    private static final String[] KEYWORDS = {"Java", "Python", "前端", "数据分析", "产品经理"};
    private static final String PYTHON_CMD = "python";

    // 状态: idle, running, failed
    private volatile String crawlerStatus = "idle";
    private volatile LocalDateTime lastStartTime = null;
    private volatile String lastMessage = "暂无更新记录";

    // 防止并发
    private final Object lock = new Object();

    @Override
    public Map<String, Object> getDataOverview() {
        Map<String, Object> result = new HashMap<>();

        // 总记录数
        Long totalCount = jobInfoMapper.selectCount(null);
        result.put("totalCount", totalCount);

        // 最后更新时间: 用最新的 createdAt 代替 crawl_date
        LambdaQueryWrapper<JobInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(JobInfo::getCreatedAt);
        wrapper.last("LIMIT 1");
        JobInfo latest = jobInfoMapper.selectOne(wrapper);
        String lastCrawlTime = "未知";
        if (latest != null && latest.getCreatedAt() != null) {
            lastCrawlTime = latest.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
        if (lastStartTime != null) {
            result.put("lastStartTime", lastStartTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

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
            lastMessage = "更新任务已启动...";
        }

        // 异步执行爬虫
        new Thread(() -> {
            try {
                runSpider();
                crawlerStatus = "idle";
                lastMessage = "上次更新成功 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                crawlerStatus = "failed";
                lastMessage = "更新失败: " + e.getMessage();
                e.printStackTrace();
            }
        }).start();

        result.put("success", true);
        result.put("message", "更新任务已启动，请稍后查看结果");
        return result;
    }

    private void runSpider() throws Exception {
        Path crawlerDir = resolveCrawlerDir();
        Path spiderPath = crawlerDir.resolve("spider.py").toAbsolutePath().normalize();
        File spiderFile = spiderPath.toFile();
        if (!spiderFile.exists()) {
            throw new RuntimeException("爬虫脚本不存在: " + spiderPath);
        }

        String python = resolvePythonExecutable(crawlerDir);
        ensureCrawlerDependencies(crawlerDir, python);

        ProcessBuilder pb = new ProcessBuilder(python, spiderPath.toString());
        pb.directory(crawlerDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[Spider] " + line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("爬虫执行失败，退出码: " + exitCode);
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
            System.out.println("[Pip] " + line);
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
            System.out.println("[EnsurePip] " + line);
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
