package com.jobdata.service;

import java.util.Map;

/**
 * 数据管理服务：提供数据概览、更新任务控制与日志管理能力。
 */
public interface DataManageService {
    /**
     * 获取数据概览信息。
     *
     * @return 概览数据
     */
    Map<String, Object> getDataOverview();

    /**
     * 启动数据更新流程。
     *
     * @return 启动结果/状态
     */
    Map<String, Object> startUpdate();

    /**
     * 确认登录（用于需要人工登录确认的更新流程）。
     *
     * @return 确认结果/状态
     */
    Map<String, Object> confirmLogin();

    Map<String, Object> stopUpdate();

    /**
     * 清空日志。
     *
     * @return 清空结果
     */
    Map<String, Object> clearLogs();
}
