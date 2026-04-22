package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("paper_tasks")
public class PaperTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_no")
    private String taskNo;

    @TableField("user_id")
    private Long userId;

    private String source;

    private String keyword;

    private String status;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("processed_count")
    private Integer processedCount;

    @TableField("quota_consumed")
    private Integer quotaConsumed;

    @TableField("result_file_url")
    private String resultFileUrl;

    @TableField("error_message")
    private String errorMessage;

    private String filters;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
