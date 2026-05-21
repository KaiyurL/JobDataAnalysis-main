package com.jobdata.service;

import java.io.File;
import java.util.Map;

public interface PipelineService {
    Map<String, Object> startDashboardPipeline();
    Map<String, Object> getPipelineStatus();
    Map<String, Object> getPipelineArtifacts();
    File getArtifactFile(String key);
}

