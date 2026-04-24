package com.paperpilot.dto.request;

import com.paperpilot.dto.AIConfigDTO;
import com.paperpilot.dto.PaperDTO;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateAnalysisTaskRequest {

    @NotEmpty(message = "文献列表不能为空")
    private List<PaperDTO> papers;

    private Boolean useUserConfig;

    private AIConfigDTO config;
}
