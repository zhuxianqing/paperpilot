package com.paperpilot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SearchTaskRequest {

    @NotBlank(message = "任务编号不能为空")
    private String taskNo;

    @NotBlank(message = "来源不能为空")
    private String source;

    @NotBlank(message = "关键词不能为空")
    private String keyword;

    private Map<String, Object> filters;

    @NotEmpty(message = "文献列表不能为空")
    private List<PaperItem> papers;

    @Data
    public static class PaperItem {
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
    }
}
