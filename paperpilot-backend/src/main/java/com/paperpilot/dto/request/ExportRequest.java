package com.paperpilot.dto.request;

import com.paperpilot.dto.PaperExportDTO;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ExportRequest {

    @NotEmpty(message = "文献列表不能为空")
    private List<PaperExportDTO> papers;

    private Boolean useUserConfig;
}
