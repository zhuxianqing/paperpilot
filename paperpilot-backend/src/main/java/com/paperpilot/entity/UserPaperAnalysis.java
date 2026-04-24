package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_paper_analyses")
public class UserPaperAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("paper_key")
    private String paperKey;

    @TableField("paper_doi")
    private String paperDoi;

    private String title;

    @TableField("title_cn")
    private String titleCn;

    @TableField("abstract_text")
    private String abstractText;

    @TableField("abstract_cn")
    private String abstractCn;

    private String authors;

    private String journal;

    @TableField("publish_year")
    private Integer publishYear;

    private Integer citations;

    @TableField("source_platform")
    private String sourcePlatform;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("pdf_url")
    private String pdfUrl;

    @TableField("summary_zh")
    private String summaryZh;

    @TableField("keywords_zh")
    private String keywordsZh;

    @TableField("methodology_zh")
    private String methodologyZh;

    @TableField("conclusion_zh")
    private String conclusionZh;

    @TableField("research_findings_zh")
    private String researchFindingsZh;

    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("analyzed_at")
    private LocalDateTime analyzedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
