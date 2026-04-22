package com.paperpilot.controller;

import com.paperpilot.dto.AIConfigDTO;
import com.paperpilot.dto.request.AIConfigRequest;
import com.paperpilot.dto.response.AIConfigVO;
import com.paperpilot.dto.response.TestConfigResponse;
import com.paperpilot.service.AIConfigService;
import com.paperpilot.service.AIService;
import com.paperpilot.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user/ai-configs")
@RequiredArgsConstructor
public class AIConfigController {

    private final AIConfigService aiConfigService;
    private final AIService aiService;

    @GetMapping
    public Result<List<AIConfigVO>> getConfigs(@AuthenticationPrincipal UserDetails user) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(aiConfigService.getUserConfigs(userId));
    }

    @PostMapping
    public Result<Void> saveConfig(@AuthenticationPrincipal UserDetails user,
                                   @RequestBody @Valid AIConfigRequest request) {
        Long userId = Long.valueOf(user.getUsername());
        aiConfigService.saveConfig(userId, request);
        return Result.success();
    }

    @DeleteMapping("/{provider}")
    public Result<Void> deleteConfig(@AuthenticationPrincipal UserDetails user,
                                     @PathVariable String provider) {
        Long userId = Long.valueOf(user.getUsername());
        aiConfigService.deleteConfig(userId, provider);
        return Result.success();
    }

    @PostMapping("/test")
    public Result<TestConfigResponse> testConfig(@RequestBody @Valid AIConfigRequest request) {
        boolean success = aiService.testAIConfig(
            AIConfigDTO.builder()
                .provider(request.getProvider())
                .apiKey(request.getApiKey())
                .baseUrl(request.getBaseUrl())
                .model(request.getModel())
                .build()
        );

        return Result.success(TestConfigResponse.builder()
            .success(success)
            .message(success ? "连接成功" : "连接失败，请检查配置")
            .build());
    }
}
