package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QuotaTransactionVO {

    private Long id;
    private Integer amount;
    private String type;
    private Long orderId;
    private Long taskId;
    private String description;
    private LocalDateTime createdAt;
}
