package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestConfigResponse {

    private Boolean success;
    private String message;
}
