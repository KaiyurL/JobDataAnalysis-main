package com.jobdata.controller;

import com.jobdata.dto.Result;
import com.jobdata.service.DataManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据管理接口：提供数据概览、更新流程控制与日志管理能力。
 */
@RestController
@RequestMapping("/api/data")
@CrossOrigin
public class DataManageController {

    @Autowired
    private DataManageService dataManageService;

    /**
     * 获取数据概览信息。
     *
     * @return 概览数据
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        try {
            return Result.success(dataManageService.getDataOverview());
        } catch (Exception e) {
            return Result.error("获取数据失败: " + e.getMessage());
        }
    }

    /**
     * 启动数据更新任务。
     *
     * @return 启动结果/状态
     */
    @PostMapping("/update")
    public Result<Map<String, Object>> startUpdate() {
        try {
            return Result.success(dataManageService.startUpdate());
        } catch (Exception e) {
            return Result.error("启动更新失败: " + e.getMessage());
        }
    }

    /**
     * 确认登录（用于需要人工登录确认的更新流程）。
     *
     * @return 确认结果/状态
     */
    @PostMapping("/confirm-login")
    public Result<Map<String, Object>> confirmLogin() {
        try {
            return Result.success(dataManageService.confirmLogin());
        } catch (Exception e) {
            return Result.error("确认登录失败: " + e.getMessage());
        }
    }

    @PostMapping("/stop")
    public Result<Map<String, Object>> stopUpdate() {
        try {
            return Result.success(dataManageService.stopUpdate());
        } catch (Exception e) {
            return Result.error("停止失败: " + e.getMessage());
        }
    }

    /**
     * 清空更新日志。
     *
     * @return 清空结果
     */
    @PostMapping("/logs/clear")
    public Result<Map<String, Object>> clearLogs() {
        try {
            return Result.success(dataManageService.clearLogs());
        } catch (Exception e) {
            return Result.error("清空日志失败: " + e.getMessage());
        }
    }
}
