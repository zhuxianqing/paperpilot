package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisTaskVO {

    private Long id;
    private String taskNo;
    private String status;
    private Integer totalCount;
    private Integer processedCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer newCount;
    private Integer reusedCount;
    private Integer reanalyzeCount;
    private Boolean useUserConfig;
    private String provider;
    private String model;
    private Integer quotaReserved;
    private Integer quotaConsumed;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
