package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QuotaVO {

    private Integer balance;
    private Integer freeQuotaUsed;
    private Integer freeQuotaTotal;
    private Boolean isVip;
    private LocalDateTime vipExpireAt;
}
