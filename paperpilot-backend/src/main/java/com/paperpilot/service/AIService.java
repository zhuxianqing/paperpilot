package com.paperpilot.service;

import com.paperpilot.dto.AIConfigDTO;
import com.paperpilot.dto.PaperDTO;
import com.paperpilot.dto.response.PaperAnalysisTranslateVO;

public interface AIService {

    PaperAnalysisTranslateVO analyzeSingleWithTranslations(Long userId, PaperDTO paper, boolean useUserConfig, String provider, String model);

    String sha256Hash(String input);

    boolean testAIConfig(AIConfigDTO configDTO);
}
