package com.paperpilot.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExportResultVO {

    private String type;           // 导出类型: excel/feishu
    private String url;            // 飞书文档URL（飞书导出时使用）
    private String fileId;         // 本地文件ID（Excel导出时使用）
    private LocalDateTime expiresAt; // 过期时间
}
