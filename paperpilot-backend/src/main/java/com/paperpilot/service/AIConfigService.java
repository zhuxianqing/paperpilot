package com.paperpilot.service;

import com.paperpilot.dto.request.AIConfigRequest;
import com.paperpilot.dto.response.AIConfigVO;

import java.util.List;

public interface AIConfigService {

    List<AIConfigVO> getUserConfigs(Long userId);

    void saveConfig(Long userId, AIConfigRequest request);

    void deleteConfig(Long userId, String provider);
}
