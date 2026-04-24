package com.paperpilot.controller;

import com.paperpilot.dto.PaperDTO;
import com.paperpilot.dto.request.CreateAnalysisTaskRequest;
import com.paperpilot.dto.response.AnalysisLookupItemVO;
import com.paperpilot.dto.response.AnalysisPaperVO;
import com.paperpilot.dto.response.AnalysisTaskVO;
import com.paperpilot.dto.response.PageResult;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.service.AnalysisTaskService;
import com.paperpilot.service.CacheService;
import com.paperpilot.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AIController {

    private final AnalysisTaskService analysisTaskService;
    private final CacheService cacheService;

    @Value("${abuse.max-duplicate-requests:3}")
    private Integer maxDuplicateRequests;

    @PostMapping("/analysis-tasks")
    public Result<AnalysisTaskVO> createAnalysisTask(@AuthenticationPrincipal UserDetails user,
                                                     @RequestBody @Valid CreateAnalysisTaskRequest request) {
        Long userId = Long.valueOf(user.getUsername());
        String requestHash = generateRequestHash(request);
        if (!cacheService.checkBehaviorPattern(userId, requestHash, maxDuplicateRequests)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "检测到异常重复请求，请稍后再试");
        }
        return Result.success(analysisTaskService.createTask(userId, request));
    }

    @GetMapping("/analysis-tasks/{taskNo}")
    public Result<AnalysisTaskVO> getAnalysisTask(@AuthenticationPrincipal UserDetails user,
                                                  @PathVariable String taskNo) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(analysisTaskService.getTask(userId, taskNo));
    }

    @GetMapping("/analysis-tasks/{taskNo}/papers")
    public Result<List<AnalysisPaperVO>> getAnalysisTaskPapers(@AuthenticationPrincipal UserDetails user,
                                                               @PathVariable String taskNo) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(analysisTaskService.getTaskPapers(userId, taskNo));
    }

    @GetMapping("/analysis-tasks/history")
    public Result<PageResult<AnalysisTaskVO>> getAnalysisTaskHistory(@AuthenticationPrincipal UserDetails user,
                                                                     @RequestParam(defaultValue = "1") Integer page,
                                                                     @RequestParam(defaultValue = "20") Integer size) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(analysisTaskService.getTaskHistory(userId, page, size));
    }

    @GetMapping("/analyses/history")
    public Result<PageResult<AnalysisPaperVO>> getAnalysisHistory(@AuthenticationPrincipal UserDetails user,
                                                                  @RequestParam(defaultValue = "1") Integer page,
                                                                  @RequestParam(defaultValue = "20") Integer size,
                                                                  @RequestParam(required = false) String sortBy,
                                                                  @RequestParam(required = false) String order,
                                                                  @RequestParam(required = false) String keyword) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(analysisTaskService.getAnalysisHistory(userId, page, size, sortBy, order, keyword));
    }

    @PostMapping("/analyses/lookup")
    public Result<List<AnalysisLookupItemVO>> lookupAnalyses(@AuthenticationPrincipal UserDetails user,
                                                             @RequestBody List<PaperDTO> papers) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(analysisTaskService.lookupAnalyses(userId, papers));
    }

    private String generateRequestHash(CreateAnalysisTaskRequest request) {
        String content = request.getPapers().stream()
                .map(p -> p.getTitle() + "|" + p.getAbstracts())
                .collect(Collectors.joining("||"));
        return cacheService.sha256Hash(content);
    }
}
