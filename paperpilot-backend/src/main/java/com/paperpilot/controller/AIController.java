package com.paperpilot.controller;

import com.paperpilot.dto.request.AnalyzeRequest;
import com.paperpilot.dto.response.PaperAnalysisVO;
import com.paperpilot.dto.response.QuotaDeductResult;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.service.AIService;
import com.paperpilot.service.CacheService;
import com.paperpilot.service.QuotaService;
import com.paperpilot.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIService aiService;
    private final QuotaService quotaService;
    private final CacheService cacheService;

    @Value("${abuse.max-duplicate-requests:3}")
    private Integer maxDuplicateRequests;

    @PostMapping("/analyze-batch")
    public Result<List<PaperAnalysisVO>> analyzeBatch(@AuthenticationPrincipal UserDetails user,
                                                      @RequestBody @Valid AnalyzeRequest request) {
        Long userId = Long.valueOf(user.getUsername());
        boolean useUserConfig = Boolean.TRUE.equals(request.getUseUserConfig());

        // 检查行为模式（重复请求检测）
        String requestHash = generateRequestHash(request);
        if (!cacheService.checkBehaviorPattern(userId, requestHash, maxDuplicateRequests)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "检测到异常重复请求，请稍后再试");
        }

        QuotaDeductResult deductResult = null;

        // 如果不是使用用户配置，检查并扣除额度
        if (!useUserConfig) {
            int amount = request.getPapers().size();
            deductResult = quotaService.deductQuota(userId, amount, "AI文献分析", null);
            if (!deductResult.isSuccess()) {
                throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
            }
        }

        try {
            List<PaperAnalysisVO> results = aiService.analyzeBatch(userId, request.getPapers(), useUserConfig, request.getConfig());
            return Result.success(results);
        } catch (Exception e) {
            // AI分析失败时回滚额度
            if (deductResult != null && deductResult.isSuccess()) {
                quotaService.rollbackQuota(userId, deductResult.getAmount(), deductResult.isUsedFreeQuota());
            }
            throw e;
        }
    }

    private String generateRequestHash(AnalyzeRequest request) {
        // 生成请求内容的哈希，用于行为模式检测
        String content = request.getPapers().stream()
                .map(p -> p.getTitle() + "|" + p.getAbstracts())
                .collect(Collectors.joining("||"));
        return cacheService.sha256Hash(content);
    }
}
