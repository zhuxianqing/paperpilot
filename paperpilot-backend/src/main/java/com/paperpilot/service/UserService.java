package com.paperpilot.service;

import com.paperpilot.dto.response.PageResult;
import com.paperpilot.dto.response.QuotaTransactionVO;
import com.paperpilot.dto.response.QuotaVO;
import com.paperpilot.dto.response.UserVO;

public interface UserService {

    UserVO getProfile(Long userId);

    QuotaVO getQuota(Long userId);

    PageResult<QuotaTransactionVO> getTransactions(Long userId, Integer page, Integer size);
}
