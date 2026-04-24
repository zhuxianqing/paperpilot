package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_task_papers")
public class AnalysisTaskPaper {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("analysis_task_id")
    private Long analysisTaskId;

    @TableField("user_paper_analysis_id")
    private Long userPaperAnalysisId;

    @TableField("result_source")
    private String resultSource;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
