package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserVO {

    private Long id;
    private String email;
    private String nickname;
    private String avatar;
    private Integer quotaBalance;
    private Boolean isVip;
    private LocalDateTime vipExpireAt;
}
