package com.paperpilot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIConfigDTO {

    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
}
