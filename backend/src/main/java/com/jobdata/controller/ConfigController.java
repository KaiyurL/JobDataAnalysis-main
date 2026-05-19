
package com.jobdata.controller;

import com.jobdata.dto.Result;
import com.jobdata.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@CrossOrigin
public class ConfigController {
    
    @Autowired
    private ConfigService configService;
    
    @GetMapping
    public Result<Map<String, Object>> getConfig() {
        try {
            return Result.success(configService.getConfig());
        } catch (Exception e) {
            return Result.error("获取配置失败: " + e.getMessage());
        }
    }
    
    @PostMapping
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> config) {
        try {
            return Result.success(configService.updateConfig(config));
        } catch (Exception e) {
            return Result.error("保存配置失败: " + e.getMessage());
        }
    }
}
