package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    private BigDecimal amount;

    @TableField("quota_amount")
    private Integer quotaAmount;

    private String channel;

    @TableField("channel_order_no")
    private String channelOrderNo;

    private Integer status;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("expire_at")
    private LocalDateTime expireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
