package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_ai_configs")
public class UserAIConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String provider;

    @TableField("api_key")
    private String apiKey;

    @TableField("base_url")
    private String baseUrl;

    private String model;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("last_tested_at")
    private LocalDateTime lastTestedAt;

    @TableField("test_status")
    private String testStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
