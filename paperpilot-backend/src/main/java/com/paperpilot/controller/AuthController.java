package com.paperpilot.controller;

import com.paperpilot.dto.request.LoginRequest;
import com.paperpilot.dto.request.RegisterRequest;
import com.paperpilot.dto.request.SendCodeRequest;
import com.paperpilot.dto.response.AuthResponse;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.service.AuthService;
import com.paperpilot.service.CacheService;
import com.paperpilot.service.VerificationCodeService;
import com.paperpilot.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;
    private final CacheService cacheService;

    @Value("${ip.limit.max-users-per-ip:5}")
    private Integer maxUsersPerIp;

    /**
     * 发送注册验证码
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody @Valid SendCodeRequest request,
                                  HttpServletRequest httpRequest) {
        // 检查IP限制
        String ip = getClientIp(httpRequest);
        if (!cacheService.checkIPLimit(ip, maxUsersPerIp)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "该IP请求过于频繁，请稍后再试");
        }

        boolean sent = verificationCodeService.sendCode(request.getEmail());
        if (!sent) {
            throw new BusinessException(ErrorCode.CODE_SEND_TOO_FREQUENT);
        }
        return Result.success();
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@RequestBody @Valid RegisterRequest request,
                                          HttpServletRequest httpRequest) {
        // 校验验证码
        boolean verified = verificationCodeService.verifyCode(request.getEmail(), request.getCode());
        if (!verified) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        String ip = getClientIp(httpRequest);
        AuthResponse response = authService.register(request, ip);

        // 注册成功后删除验证码
        verificationCodeService.deleteCode(request.getEmail());

        return Result.success(response);
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        AuthResponse response = authService.login(request);
        return Result.success(response);
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refreshToken(@RequestHeader("X-Refresh-Token") String refreshToken) {
        AuthResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        // 这里可以实现token黑名单逻辑
        return Result.success();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多个IP（代理情况），取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
