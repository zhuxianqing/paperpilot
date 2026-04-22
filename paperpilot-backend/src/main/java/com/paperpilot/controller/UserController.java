package com.paperpilot.controller;

import com.paperpilot.dto.response.PageResult;
import com.paperpilot.dto.response.QuotaTransactionVO;
import com.paperpilot.dto.response.QuotaVO;
import com.paperpilot.dto.response.UserVO;
import com.paperpilot.service.UserService;
import com.paperpilot.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public Result<UserVO> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return Result.success(userService.getProfile(userId));
    }

    @GetMapping("/quota")
    public Result<QuotaVO> getQuota(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return Result.success(userService.getQuota(userId));
    }

    @GetMapping("/transactions")
    public Result<PageResult<QuotaTransactionVO>> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return Result.success(userService.getTransactions(userId, page, size));
    }
}
