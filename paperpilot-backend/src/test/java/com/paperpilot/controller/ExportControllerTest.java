package com.paperpilot.controller;

import com.paperpilot.service.ExportService;
import com.paperpilot.service.LocalFileService;
import com.paperpilot.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExportControllerTest {

    @Mock
    private ExportService exportService;

    @Mock
    private QuotaService quotaService;

    @Mock
    private LocalFileService localFileService;

    @InjectMocks
    private ExportController exportController;

    @Test
    void shouldNormalizeLeadingSlashWhenDownloadingExcel() {
        String fileId = "/2026-04-18/user_0_1776520196246.xlsx";
        MockHttpServletResponse response = new MockHttpServletResponse();

        exportController.downloadExcel(fileId, null, response);

        verify(localFileService).downloadFile(eq("2026-04-18/user_0_1776520196246.xlsx"),
                same(response), eq("paperpilot_export.xlsx"));
    }
}
