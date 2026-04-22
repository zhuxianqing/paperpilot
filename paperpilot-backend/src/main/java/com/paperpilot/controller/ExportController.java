package com.paperpilot.controller;

import com.paperpilot.dto.request.ExportRequest;
import com.paperpilot.dto.response.ExportResultVO;
import com.paperpilot.dto.response.QuotaDeductResult;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.service.ExportService;
import com.paperpilot.service.LocalFileService;
import com.paperpilot.service.QuotaService;
import com.paperpilot.util.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final QuotaService quotaService;
    private final LocalFileService localFileService;

    @PostMapping("/feishu")
    public Result<ExportResultVO> exportToFeishu(@AuthenticationPrincipal UserDetails user,
                                                  @RequestBody @Valid ExportRequest request) {
        Long userId = Long.valueOf(user.getUsername());

        // 非BYOK用户检查额度
        if (!Boolean.TRUE.equals(request.getUseUserConfig())) {
            QuotaDeductResult deductResult = quotaService.deductQuota(userId, request.getPapers().size(),
                                          "导出飞书文档", null);
            if (!deductResult.isSuccess()) {
                throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
            }
        }

        ExportResultVO result = exportService.exportToFeishu(request.getPapers());
        return Result.success(result);
    }

    @PostMapping("/excel")
    public Result<ExportResultVO> exportToExcel(@AuthenticationPrincipal UserDetails user,
                                                 @RequestBody @Valid ExportRequest request) {
        Long userId = Long.valueOf(user.getUsername());

        // 非BYOK用户检查额度
        if (!Boolean.TRUE.equals(request.getUseUserConfig())) {
            QuotaDeductResult deductResult = quotaService.deductQuota(userId, request.getPapers().size(),
                                          "导出Excel", null);
            if (!deductResult.isSuccess()) {
                throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
            }
        }

        ExportResultVO result = exportService.exportToExcel(request.getPapers());
        return Result.success(result);
    }

    /**
     * 下载Excel文件（本地存储模式）
     */
    @GetMapping("/download/{*fileId}")
    public void downloadExcel(
            @PathVariable("fileId") String fileId,
            @AuthenticationPrincipal UserDetails user,
            HttpServletResponse response) {

        // TODO: 校验用户是否有权下载该文件（可选：记录文件与用户关联）
        String normalizedFileId = fileId.startsWith("/") ? fileId.substring(1) : fileId;

        localFileService.downloadFile(normalizedFileId, response, "paperpilot_export.xlsx");
    }
}
