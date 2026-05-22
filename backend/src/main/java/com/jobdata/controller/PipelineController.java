package com.jobdata.controller;

import com.jobdata.dto.Result;
import com.jobdata.service.PipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/pipeline")
@CrossOrigin
public class PipelineController {

    @Autowired
    private PipelineService pipelineService;

    @PostMapping("/dashboard/run")
    public Result<?> runDashboard(@RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        try {
            return Result.success(pipelineService.startDashboardPipeline(force));
        } catch (Exception e) {
            return Result.error("启动 Pipeline 失败: " + e.getMessage());
        }
    }

    @PostMapping("/stats/run")
    public Result<?> runStats(@RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        try {
            return Result.success(pipelineService.startStatsPipeline(force));
        } catch (Exception e) {
            return Result.error("启动 Stats 失败: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    public Result<?> status() {
        try {
            return Result.success(pipelineService.getPipelineStatus());
        } catch (Exception e) {
            return Result.error("获取状态失败: " + e.getMessage());
        }
    }

    @GetMapping("/artifacts")
    public Result<?> artifacts() {
        try {
            return Result.success(pipelineService.getPipelineArtifacts());
        } catch (Exception e) {
            return Result.error("获取结果失败: " + e.getMessage());
        }
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> file(@RequestParam("key") String key) {
        try {
            File f = pipelineService.getArtifactFile(key);
            if (f == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            byte[] bytes = Files.readAllBytes(f.toPath());
            MediaType contentType = guessContentType(f.getName());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setCacheControl("no-store");
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private MediaType guessContentType(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (n.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (n.endsWith(".csv")) {
            return MediaType.valueOf("text/csv");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
