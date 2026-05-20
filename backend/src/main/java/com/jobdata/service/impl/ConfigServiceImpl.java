
package com.jobdata.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobdata.service.ConfigService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class ConfigServiceImpl implements ConfigService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path resolveCrawlerDir() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path[] candidates = new Path[]{
                current.resolve("crawler"),
                current.resolve("..").resolve("crawler"),
                current.resolve("..").resolve("..").resolve("crawler")
        };
        for (Path candidate : candidates) {
            if (candidate.resolve("spider.py").toFile().exists()) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return candidates[0].toAbsolutePath().normalize();
    }

    private File resolveConfigFile() {
        return resolveCrawlerDir().resolve("config.json").toFile();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfig() {
        try {
            File file = resolveConfigFile();
            if (!file.exists()) {
                // 返回默认配置
                Map<String, Object> defaultConfig = new HashMap<>();
                defaultConfig.put("platform", "boss");
                defaultConfig.put("browser", "auto");
                defaultConfig.put("keywords", new String[]{"Java", "Python", "前端", "数据分析", "产品经理"});
                defaultConfig.put("cities", new String[]{"北京", "上海", "广州", "深圳", "杭州"});
                defaultConfig.put("pages_per_keyword", 2);
                defaultConfig.put("pages_per_city_51job", 2);
                defaultConfig.put("delay_min", 3);
                defaultConfig.put("delay_max", 8);
                Map<String, String> cityCodes51 = new HashMap<>();
                cityCodes51.put("北京", "010000");
                cityCodes51.put("上海", "020000");
                cityCodes51.put("广州", "030200");
                cityCodes51.put("深圳", "040000");
                cityCodes51.put("杭州", "080200");
                cityCodes51.put("苏州", "070300");
                cityCodes51.put("南京", "070200");
                cityCodes51.put("成都", "090200");
                cityCodes51.put("武汉", "180200");
                cityCodes51.put("西安", "200200");
                cityCodes51.put("重庆", "060000");
                cityCodes51.put("天津", "050000");
                cityCodes51.put("郑州", "170200");
                cityCodes51.put("长沙", "190200");
                cityCodes51.put("青岛", "120200");
                cityCodes51.put("大连", "230200");
                cityCodes51.put("厦门", "110200");
                cityCodes51.put("宁波", "080300");
                cityCodes51.put("无锡", "070400");
                cityCodes51.put("合肥", "150200");
                cityCodes51.put("福州", "110300");
                defaultConfig.put("city_codes_51job", cityCodes51);
                return defaultConfig;
            }
            Map<String, Object> config = objectMapper.readValue(file, Map.class);

            config.putIfAbsent("platform", "boss");
            config.putIfAbsent("browser", "auto");
            config.putIfAbsent("pages_per_city_51job", 2);

            Object codesObj = config.get("city_codes_51job");
            boolean missingCodes = true;
            if (codesObj instanceof Map) {
                missingCodes = ((Map<?, ?>) codesObj).isEmpty();
            }
            if (codesObj instanceof Map && !((Map<?, ?>) codesObj).isEmpty()) {
                missingCodes = false;
            }

            if (missingCodes) {
                Map<String, String> cityCodes51 = new HashMap<>();
                cityCodes51.put("北京", "010000");
                cityCodes51.put("上海", "020000");
                cityCodes51.put("广州", "030200");
                cityCodes51.put("深圳", "040000");
                cityCodes51.put("杭州", "080200");
                cityCodes51.put("苏州", "070300");
                cityCodes51.put("南京", "070200");
                cityCodes51.put("成都", "090200");
                cityCodes51.put("武汉", "180200");
                cityCodes51.put("西安", "200200");
                cityCodes51.put("重庆", "060000");
                cityCodes51.put("天津", "050000");
                cityCodes51.put("郑州", "170200");
                cityCodes51.put("长沙", "190200");
                cityCodes51.put("青岛", "120200");
                cityCodes51.put("大连", "230200");
                cityCodes51.put("厦门", "110200");
                cityCodes51.put("宁波", "080300");
                cityCodes51.put("无锡", "070400");
                cityCodes51.put("合肥", "150200");
                cityCodes51.put("福州", "110300");
                config.put("city_codes_51job", cityCodes51);
            }

            return config;
        } catch (IOException e) {
            throw new RuntimeException("读取配置文件失败", e);
        }
    }
    
    @Override
    public Map<String, Object> updateConfig(Map<String, Object> config) {
        try {
            File file = resolveConfigFile();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, config);
            return getConfig();
        } catch (IOException e) {
            throw new RuntimeException("保存配置文件失败", e);
        }
    }
}
