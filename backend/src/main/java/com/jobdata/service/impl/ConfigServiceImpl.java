
package com.jobdata.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.service.ConfigService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class ConfigServiceImpl implements ConfigService {
    
    private static final String CONFIG_PATH = "../crawler/config.json";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfig() {
        try {
            File file = new File(CONFIG_PATH);
            if (!file.exists()) {
                // 返回默认配置
                Map<String, Object> defaultConfig = new HashMap<>();
                defaultConfig.put("keywords", new String[]{"Java", "Python", "前端", "数据分析", "产品经理"});
                defaultConfig.put("cities", new String[]{"北京", "上海", "广州", "深圳", "杭州"});
                defaultConfig.put("pages_per_keyword", 2);
                defaultConfig.put("delay_min", 3);
                defaultConfig.put("delay_max", 8);
                return defaultConfig;
            }
            return objectMapper.readValue(file, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败", e);
        }
    }
    
    @Override
    public Map<String, Object> updateConfig(Map<String, Object> config) {
        try {
            File file = new File(CONFIG_PATH);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, config);
            return getConfig();
        } catch (IOException e) {
            throw new RuntimeException("保存配置文件失败", e);
        }
    }
}
