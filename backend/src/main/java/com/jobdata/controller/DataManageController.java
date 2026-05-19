package com.jobdata.controller;

import com.jobdata.dto.Result;
import com.jobdata.service.DataManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/data")
@CrossOrigin
public class DataManageController {

    @Autowired
    private DataManageService dataManageService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview() {
        try {
            return Result.success(dataManageService.getDataOverview());
        } catch (Exception e) {
            return Result.error("获取数据失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public Result<Map<String, Object>> startUpdate() {
        try {
            return Result.success(dataManageService.startUpdate());
        } catch (Exception e) {
            return Result.error("启动更新失败: " + e.getMessage());
        }
    }
}
