package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("papers")
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    private String doi;

    private String title;

    private String authors;

    private String abstracts;

    private String journal;

    @TableField("publish_year")
    private Integer publishYear;

    @TableField("impact_factor")
    private BigDecimal impactFactor;

    private String quartile;

    private Integer citations;

    @TableField("pdf_url")
    private String pdfUrl;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("ai_summary")
    private String aiSummary;

    @TableField("ai_keywords")
    private String aiKeywords;

    private String methodology;

    private String conclusion;

    @TableField("research_findings")
    private String researchFindings;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
