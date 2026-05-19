
package com.jobdata.service;

import java.util.Map;

public interface ConfigService {
    Map<String, Object> getConfig();
    Map<String, Object> updateConfig(Map<String, Object> config);
}
