package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_logs")
public class AILog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String provider;

    private String model;

    @TableField("input_tokens")
    private Integer inputTokens;

    @TableField("output_tokens")
    private Integer outputTokens;

    @TableField("request_hash")
    private String requestHash;

    @TableField("cache_hit")
    private Boolean cacheHit;

    @TableField("cost_amount")
    private java.math.BigDecimal costAmount;

    @TableField("response_time_ms")
    private Integer responseTimeMs;

    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
