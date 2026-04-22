package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaperAnalysisVO {

    private String doi;
    private String title;
    private String aiSummary;
    private List<String> keyPoints;
    private String methodology;
    private String conclusion;
    private String researchFindings;
    private List<String> aiKeywords;
}
