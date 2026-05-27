package com.jobdata.service;

import java.io.File;
import java.util.Map;

/**
 * Pipeline 服务：负责触发离线任务并提供任务状态/产物查询能力。
 */
public interface PipelineService {
    /**
     * 启动 dashboard pipeline（使用默认策略）。
     */
    Map<String, Object> startDashboardPipeline();

    /**
     * 启动 dashboard pipeline。
     *
     * @param force 是否强制执行
     * @return 任务状态信息
     */
    Map<String, Object> startDashboardPipeline(boolean force);

    /**
     * 启动 stats pipeline（使用默认策略）。
     */
    Map<String, Object> startStatsPipeline();

    /**
     * 启动 stats pipeline。
     *
     * @param force 是否强制执行
     * @return 任务状态信息
     */
    Map<String, Object> startStatsPipeline(boolean force);

    /**
     * 获取 pipeline 执行状态。
     */
    Map<String, Object> getPipelineStatus();

    /**
     * 获取 pipeline 产物信息。
     */
    Map<String, Object> getPipelineArtifacts();

    /**
     * 获取指定 key 对应的产物文件。
     *
     * @param key 产物标识
     * @return 文件（不存在返回 null）
     */
    File getArtifactFile(String key);
}
