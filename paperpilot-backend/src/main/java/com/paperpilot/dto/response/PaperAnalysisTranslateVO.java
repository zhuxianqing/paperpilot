package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PaperAnalysisTranslateVO {

    private String doi;
    private String title;
    private String titleCn;
    private String abstractText;
    private String abstractCn;
    private String summaryZh;
    private String methodologyZh;
    private String conclusionZh;
    private String researchFindingsZh;
    private List<String> aiKeywords;
}
