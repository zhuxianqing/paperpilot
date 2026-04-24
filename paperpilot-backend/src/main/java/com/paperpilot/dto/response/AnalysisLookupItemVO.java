package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisLookupItemVO {

    private String paperKey;
    private String paperDoi;
    private Boolean analyzed;
    private LocalDateTime analyzedAt;
    private String status;
}
