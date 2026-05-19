package com.jobdata.service;

import java.util.Map;

public interface DataManageService {
    Map<String, Object> getDataOverview();
    Map<String, Object> startUpdate();
    Map<String, Object> confirmLogin();
    Map<String, Object> clearLogs();
}
