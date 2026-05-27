
package com.jobdata.service;

import java.util.Map;

/**
 * 配置服务：读取与更新系统配置。
 */
public interface ConfigService {
    /**
     * 获取当前配置。
     *
     * @return 配置键值对
     */
    Map<String, Object> getConfig();

    /**
     * 更新配置。
     *
     * @param config 新配置键值对
     * @return 更新后的配置
     */
    Map<String, Object> updateConfig(Map<String, Object> config);
}
