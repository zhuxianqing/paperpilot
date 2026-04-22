package com.paperpilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paperpilot.dto.response.PageResult;
import com.paperpilot.dto.response.QuotaTransactionVO;
import com.paperpilot.dto.response.QuotaVO;
import com.paperpilot.dto.response.UserVO;
import com.paperpilot.entity.QuotaTransaction;
import com.paperpilot.entity.User;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.mapper.QuotaTransactionMapper;
import com.paperpilot.mapper.UserMapper;
import com.paperpilot.service.QuotaService;
import com.paperpilot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final QuotaService quotaService;
    private final QuotaTransactionMapper transactionMapper;

    @Override
    public UserVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

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

    @Override
    public QuotaVO getQuota(Long userId) {
        return quotaService.getQuotaInfo(userId);
    }

    @Override
    public PageResult<QuotaTransactionVO> getTransactions(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<QuotaTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuotaTransaction::getUserId, userId)
                .orderByDesc(QuotaTransaction::getCreatedAt);

        Page<QuotaTransaction> pageResult = transactionMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<QuotaTransactionVO> records = pageResult.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(records, pageResult.getTotal(), page, size);
    }

    private QuotaTransactionVO convertToVO(QuotaTransaction transaction) {
        return QuotaTransactionVO.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .orderId(transaction.getOrderId())
                .taskId(transaction.getTaskId())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
