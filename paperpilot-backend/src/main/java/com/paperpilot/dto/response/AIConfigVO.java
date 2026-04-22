package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AIConfigVO {

    private Long id;
    private String provider;
    private String baseUrl;
    private String model;
    private Boolean isActive;
    private LocalDateTime lastTestedAt;
    private String testStatus;
}
