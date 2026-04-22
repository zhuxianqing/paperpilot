package com.paperpilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperpilot.dto.request.LoginRequest;
import com.paperpilot.dto.request.RegisterRequest;
import com.paperpilot.dto.response.AuthResponse;
import com.paperpilot.dto.response.UserVO;
import com.paperpilot.entity.User;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.mapper.UserMapper;
import com.paperpilot.security.JwtTokenProvider;
import com.paperpilot.service.AuthService;
import com.paperpilot.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CacheService cacheService;

    @Value("${ip.limit.max-users-per-ip:5}")
    private Integer maxUsersPerIp;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, String ip) {
        // 检查IP限制
        if (ip != null && !cacheService.checkIPLimit(ip, maxUsersPerIp)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "该IP注册次数过多，请稍后再试");
        }

        // 检查邮箱是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, request.getEmail());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.USER_EXISTS);
        }

        // 创建用户
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getEmail().split("@")[0]);
        user.setQuotaBalance(0);
        user.setIsVip(false);
        user.setStatus(1);
        userMapper.insert(user);

        // 生成Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400L)
                .user(convertToVO(user))
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, request.getEmail());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 生成Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(86400L)
                .user(convertToVO(user))
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        // 验证refresh token
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);

        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 生成新的Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(86400L)
                .user(convertToVO(user))
                .build();
    }

    @Override
    public void logout(Long userId) {
        // 这里可以实现token黑名单等逻辑
        log.info("User {} logged out", userId);
    }

    private UserVO convertToVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .quotaBalance(user.getQuotaBalance())
                .isVip(user.getIsVip())
                .vipExpireAt(user.getVipExpireAt())
                .build();
    }
}
