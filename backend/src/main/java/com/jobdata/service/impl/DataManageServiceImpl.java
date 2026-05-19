package com.jobdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.jobdata.entity.JobInfo;
import com.jobdata.mapper.JobInfoMapper;
import com.jobdata.service.DataManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DataManageServiceImpl implements DataManageService {

    @Autowired
    private JobInfoMapper jobInfoMapper;

    private static final String[] KEYWORDS = {"Java", "Python", "前端", "数据分析", "产品经理"};
    private static final String SPIDER_PATH = "F:\\JobDataAnalysis\\crawler\\spider.py";
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
        File spiderFile = new File(SPIDER_PATH);
        if (!spiderFile.exists()) {
            throw new RuntimeException("爬虫脚本不存在: " + SPIDER_PATH);
        }

        ProcessBuilder pb = new ProcessBuilder(PYTHON_CMD, SPIDER_PATH);
        pb.directory(new File("F:\\JobDataAnalysis\\crawler"));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 读取输出（可选，用于调试）
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
}
