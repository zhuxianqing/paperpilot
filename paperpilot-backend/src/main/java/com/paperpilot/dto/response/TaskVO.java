package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TaskVO {

    private Long id;
    private String taskNo;
    private String source;
    private String keyword;
    private String status;
    private Integer totalCount;
    private Integer processedCount;
    private Integer quotaConsumed;
    private Map<String, Object> filters;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class PaperVO {
        private Long id;
        private String doi;
        private String title;
        private List<String> authors;
        private String abstractText;
        private String journal;
        private Integer publishYear;
        private Double impactFactor;
        private String quartile;
        private Integer citations;
        private String pdfUrl;
        private String sourceUrl;
        private String aiSummary;
        private List<String> aiKeywords;
        private String methodology;
        private String conclusion;
        private String researchFindings;
    }
}
