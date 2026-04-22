package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuotaDeductResult {

    private boolean success;
    private boolean usedFreeQuota;
    private int amount;
}
