package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisPaperVO {

    private Long id;
    private String paperKey;
    private String paperDoi;
    private String title;
    private String titleCn;
    private String abstractText;
    private String abstractCn;
    private String authors;
    private String journal;
    private Integer publishYear;
    private Integer citations;
    private String sourcePlatform;
    private String sourceUrl;
    private String pdfUrl;
    private String summaryZh;
    private String keywordsZh;
    private String methodologyZh;
    private String resultSource;
    private Integer displayOrder;
    private String conclusionZh;
    private String researchFindingsZh;
    private String status;
    private String errorMessage;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
