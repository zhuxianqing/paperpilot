package com.paperpilot.service;

import com.paperpilot.dto.AIConfigDTO;
import com.paperpilot.dto.PaperDTO;
import com.paperpilot.dto.response.PaperAnalysisVO;

import java.util.List;

public interface AIService {

    List<PaperAnalysisVO> analyzeBatch(Long userId, List<PaperDTO> papers, boolean useUserConfig, AIConfigDTO userConfig);

    boolean testAIConfig(AIConfigDTO configDTO);
}
