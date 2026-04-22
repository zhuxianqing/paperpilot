package com.paperpilot.service;

import com.paperpilot.dto.response.QuotaDeductResult;
import com.paperpilot.dto.response.QuotaVO;

public interface QuotaService {

    QuotaVO getQuotaInfo(Long userId);

    QuotaDeductResult deductQuota(Long userId, int amount, String reason, Long taskId);

    void addQuota(Long userId, int amount, String reason, Long orderId);

    void rollbackQuota(Long userId, int amount, boolean usedFreeQuota);

    boolean checkByokDailyLimit(Long userId);
}
