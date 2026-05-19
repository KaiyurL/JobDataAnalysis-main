package com.jobdata.service;

import java.util.Map;

public interface DataManageService {
    Map<String, Object> getDataOverview();
    Map<String, Object> startUpdate();
}
