package com.paperpilot.service;

import com.paperpilot.dto.request.LoginRequest;
import com.paperpilot.dto.request.RegisterRequest;
import com.paperpilot.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request, String ip);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(String refreshToken);

    void logout(Long userId);
}
