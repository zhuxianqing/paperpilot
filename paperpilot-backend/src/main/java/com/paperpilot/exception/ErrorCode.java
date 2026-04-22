package com.paperpilot.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    INVALID_PARAMETER(400, "参数无效"),

    // 认证相关
    USER_EXISTS(1001, "用户已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    INVALID_CREDENTIALS(1003, "用户名或密码错误"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    TOKEN_INVALID(1005, "Token无效"),
    INVALID_VERIFICATION_CODE(1006, "验证码错误或已过期"),
    CODE_SEND_TOO_FREQUENT(1007, "验证码发送过于频繁，请稍后再试"),

    // 额度相关
    QUOTA_INSUFFICIENT(2001, "额度不足"),
    QUOTA_DEDUCT_FAILED(2002, "额度扣减失败"),

    // AI配置相关
    AI_CONFIG_NOT_FOUND(3001, "AI配置不存在"),
    AI_CONFIG_DECRYPT_ERROR(3002, "API Key解密失败"),
    AI_CONFIG_TEST_FAILED(3003, "AI配置测试失败"),
    BYOK_DAILY_LIMIT_EXCEEDED(3004, "今日BYOK调用次数已达上限"),
    TOO_MANY_REQUESTS(3005, "请求过于频繁，请稍后再试"),

    // AI分析相关
    AI_ANALYSIS_FAILED(4001, "AI分析失败"),

    // 导出相关
    EXPORT_FAILED(5001, "导出失败"),
    FILE_NOT_FOUND(5002, "文件不存在或已过期"),

    // 飞书相关
    FEISHU_AUTH_FAILED(6001, "飞书认证失败"),
    FEISHU_CREATE_FAILED(6002, "飞书文档创建失败"),
    FEISHU_WRITE_FAILED(6003, "飞书文档写入失败"),

    // 支付相关
    ORDER_NOT_FOUND(7001, "订单不存在"),
    ORDER_EXPIRED(7002, "订单已过期"),
    ORDER_PAID(7003, "订单已支付"),
    PAYMENT_FAILED(7004, "支付失败");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
