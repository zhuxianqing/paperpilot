package com.paperpilot.service;

import com.paperpilot.dto.PaperExportDTO;
import com.paperpilot.dto.response.ExportResultVO;

import java.util.List;

public interface ExportService {

    ExportResultVO exportToFeishu(List<PaperExportDTO> papers);

    ExportResultVO exportToExcel(List<PaperExportDTO> papers);
}
