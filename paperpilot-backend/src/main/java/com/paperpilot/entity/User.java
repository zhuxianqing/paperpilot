package com.paperpilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    @TableField("password_hash")
    private String passwordHash;

    private String nickname;

    private String phone;

    private String avatar;

    @TableField("quota_balance")
    private Integer quotaBalance;

    @TableField("is_vip")
    private Boolean isVip;

    @TableField("vip_expire_at")
    private LocalDateTime vipExpireAt;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
