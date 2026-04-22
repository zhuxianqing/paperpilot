package com.paperpilot.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AIConfigRequest {

    @NotBlank(message = "提供商不能为空")
    private String provider;

    @NotBlank(message = "API Key不能为空")
    private String apiKey;

    private String baseUrl;

    @NotBlank(message = "模型不能为空")
    private String model;

    private Boolean isDefault;
}
