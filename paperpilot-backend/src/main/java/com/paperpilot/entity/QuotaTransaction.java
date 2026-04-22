package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quota_transactions")
public class QuotaTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private Integer amount;

    private String type;

    @TableField("order_id")
    private Long orderId;

    @TableField("task_id")
    private Long taskId;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
