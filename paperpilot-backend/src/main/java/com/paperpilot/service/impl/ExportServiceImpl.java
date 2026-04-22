package com.paperpilot.service.impl;

import com.paperpilot.dto.PaperExportDTO;
import com.paperpilot.dto.response.ExportResultVO;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.service.ExportService;
import com.paperpilot.service.LocalFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final LocalFileService localFileService;
    private final WebClient webClient;

    @Value("${feishu.app-id}")
    private String feishuAppId;

    @Value("${feishu.app-secret}")
    private String feishuAppSecret;

    private String feishuAccessToken;
    private LocalDateTime tokenExpireTime;

    @Override
    public ExportResultVO exportToExcel(List<PaperExportDTO> papers) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("文献分析");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle dataAltStyle = createDataAltStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);
            CellStyle wrapAltStyle = createWrapAltStyle(workbook);
            CellStyle centerStyle = createCenterStyle(workbook);
            CellStyle centerAltStyle = createCenterAltStyle(workbook);
            CellStyle linkStyle = createLinkStyle(workbook);
            CellStyle linkAltStyle = createLinkAltStyle(workbook);

            // 表头
            String[] headers = {"序号", "标题", "AI总结", "研究方法", "研究结论",
                "研究成果", "关键词", "期刊", "分区", "年份", "被引", "原文链接"};
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 冻结前2列和首行
            sheet.createFreezePane(2, 1);

            for (int i = 0; i < papers.size(); i++) {
                PaperExportDTO p = papers.get(i);
                Row row = sheet.createRow(i + 1);
                row.setHeightInPoints(80);

                boolean isAlt = (i % 2 == 1);
                CellStyle baseStyle = isAlt ? dataAltStyle : dataStyle;
                CellStyle baseWrap = isAlt ? wrapAltStyle : wrapStyle;
                CellStyle baseCenter = isAlt ? centerAltStyle : centerStyle;
                CellStyle baseLinkStyle = isAlt ? linkAltStyle : linkStyle;

                // 序号
                Cell idxCell = row.createCell(0);
                idxCell.setCellValue(i + 1);
                idxCell.setCellStyle(baseCenter);

                // 标题（带超链接）
                Cell titleCell = row.createCell(1);
                titleCell.setCellValue(p.getTitle() != null ? p.getTitle() : "");
                titleCell.setCellStyle(baseWrap);
                if (p.getSourceUrl() != null && !p.getSourceUrl().isEmpty()) {
                    Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
                    link.setAddress(p.getSourceUrl());
                    titleCell.setHyperlink(link);
                    titleCell.setCellStyle(baseLinkStyle);
                }

                // AI总结
                Cell summaryCell = row.createCell(2);
                summaryCell.setCellValue(p.getAiSummary() != null ? p.getAiSummary() : "");
                summaryCell.setCellStyle(baseWrap);

                // 研究方法
                Cell methodCell = row.createCell(3);
                methodCell.setCellValue(p.getMethodology() != null ? p.getMethodology() : "");
                methodCell.setCellStyle(baseWrap);

                // 研究结论
                Cell conclusionCell = row.createCell(4);
                conclusionCell.setCellValue(p.getConclusion() != null ? p.getConclusion() : "");
                conclusionCell.setCellStyle(baseWrap);

                // 研究成果
                Cell findingsCell = row.createCell(5);
                findingsCell.setCellValue(p.getResearchFindings() != null ? p.getResearchFindings() : "");
                findingsCell.setCellStyle(baseWrap);

                // 关键词
                Cell kwCell = row.createCell(6);
                kwCell.setCellValue(String.join(", ", p.getAiKeywords() != null ? p.getAiKeywords() : new ArrayList<>()));
                kwCell.setCellStyle(baseWrap);

                // 期刊
                Cell journalCell = row.createCell(7);
                journalCell.setCellValue(p.getJournal() != null ? p.getJournal() : "");
                journalCell.setCellStyle(baseWrap);

                // 分区
                Cell quartileCell = row.createCell(8);
                quartileCell.setCellValue(p.getQuartile() != null ? p.getQuartile() : "");
                quartileCell.setCellStyle(baseCenter);

                // 年份
                Cell yearCell = row.createCell(9);
                if (p.getPublishYear() != null) yearCell.setCellValue(p.getPublishYear());
                yearCell.setCellStyle(baseCenter);

                // 被引
                Cell citCell = row.createCell(10);
                if (p.getCitations() != null) citCell.setCellValue(p.getCitations());
                citCell.setCellStyle(baseCenter);

                // 原文链接
                Cell urlCell = row.createCell(11);
                if (p.getSourceUrl() != null && !p.getSourceUrl().isEmpty()) {
                    urlCell.setCellValue("查看原文");
                    urlCell.setCellStyle(baseLinkStyle);
                    Hyperlink urlLink = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
                    urlLink.setAddress(p.getSourceUrl());
                    urlCell.setHyperlink(urlLink);
                } else {
                    urlCell.setCellValue("");
                    urlCell.setCellStyle(baseCenter);
                }
            }

            // 列宽（单位：1/256 字符宽度）
            sheet.setColumnWidth(0, 6 * 256);
            sheet.setColumnWidth(1, 40 * 256);
            sheet.setColumnWidth(2, 35 * 256);
            sheet.setColumnWidth(3, 28 * 256);
            sheet.setColumnWidth(4, 28 * 256);
            sheet.setColumnWidth(5, 28 * 256);
            sheet.setColumnWidth(6, 22 * 256);
            sheet.setColumnWidth(7, 18 * 256);
            sheet.setColumnWidth(8, 8 * 256);
            sheet.setColumnWidth(9, 8 * 256);
            sheet.setColumnWidth(10, 8 * 256);
            sheet.setColumnWidth(11, 12 * 256);

            workbook.write(outputStream);

            byte[] content = outputStream.toByteArray();

            // 保存到本地文件系统
            String fileId = localFileService.saveFile(content, "xlsx", 0L);

            // 获取过期时间
            LocalDateTime expiresAt = localFileService.getExpiryTime(fileId);

            return ExportResultVO.builder()
                .type("excel")
                .fileId(fileId)
                .expiresAt(expiresAt)
                .build();

        } catch (Exception e) {
            log.error("Excel export failed", e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }
    }

    @Override
    public ExportResultVO exportToFeishu(List<PaperExportDTO> papers) {
        try {
            String token = getFeishuAccessToken();
            String spreadsheetToken = createFeishuSpreadsheet(token, "文献分析_" + LocalDate.now());
            writeDataToFeishu(token, spreadsheetToken, papers);

            String docUrl = "https://docs.feishu.cn/sheets/" + spreadsheetToken;

            return ExportResultVO.builder()
                .type("feishu")
                .url(docUrl)
                .build();

        } catch (Exception e) {
            log.error("Feishu export failed", e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }
    }

    private synchronized String getFeishuAccessToken() {
        if (feishuAccessToken != null && LocalDateTime.now().isBefore(tokenExpireTime)) {
            return feishuAccessToken;
        }

        Map<String, String> requestBody = Map.of(
            "app_id", feishuAppId,
            "app_secret", feishuAppSecret
        );

        Map response = webClient.post()
            .uri("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response != null && Integer.valueOf(0).equals(response.get("code"))) {
            feishuAccessToken = (String) response.get("tenant_access_token");
            int expire = (int) response.get("expire");
            tokenExpireTime = LocalDateTime.now().plusSeconds(expire - 60);
            return feishuAccessToken;
        }

        throw new BusinessException(ErrorCode.FEISHU_AUTH_FAILED);
    }

    private String createFeishuSpreadsheet(String token, String title) {
        Map<String, Object> requestBody = Map.of(
            "title", title,
            "folders", List.of()
        );

        Map response = webClient.post()
            .uri("https://open.feishu.cn/open-apis/sheets/v3/spreadsheets")
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response != null && Integer.valueOf(0).equals(response.get("code"))) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> spreadsheet = (Map<String, Object>) data.get("spreadsheet");
            return (String) spreadsheet.get("spreadsheet_token");
        }

        throw new BusinessException(ErrorCode.FEISHU_CREATE_FAILED);
    }

    private void writeDataToFeishu(String token, String spreadsheetToken, List<PaperExportDTO> papers) {
        List<List<Object>> values = new ArrayList<>();

        values.add(List.of("序号", "标题", "AI总结", "研究方法", "研究结论",
            "研究成果", "关键词", "期刊", "分区", "年份", "被引"));

        for (int i = 0; i < papers.size(); i++) {
            PaperExportDTO p = papers.get(i);
            values.add(List.of(
                i + 1,
                p.getTitle(),
                p.getAiSummary(),
                p.getMethodology(),
                p.getConclusion(),
                p.getResearchFindings(),
                String.join(", ", p.getAiKeywords() != null ? p.getAiKeywords() : new ArrayList<>()),
                p.getJournal(),
                p.getQuartile(),
                p.getPublishYear(),
                p.getCitations()
            ));
        }

        String range = "sheet1!A1:K" + values.size();

        Map<String, Object> requestBody = Map.of(
            "valueRange", Map.of(
                "range", range,
                "values", values
            )
        );

        Map response = webClient.put()
            .uri("https://open.feishu.cn/open-apis/sheets/v2/spreadsheets/"
                 + spreadsheetToken + "/values")
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

        if (response == null || !Integer.valueOf(0).equals(response.get("code"))) {
            throw new BusinessException(ErrorCode.FEISHU_WRITE_FAILED);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        // 深蓝色背景 #2C5282
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x2C, (byte) 0x52, (byte) 0x82}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setBorderThin(style);
        return style;
    }

    private CellStyle createDataAltStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        // 交替行浅蓝色 #EBF4FF
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xEB, (byte) 0xF4, (byte) 0xFF}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorderThin(style);
        return style;
    }

    private CellStyle createWrapStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        setBorderThin(style);
        return style;
    }

    private CellStyle createWrapAltStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xEB, (byte) 0xF4, (byte) 0xFF}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorderThin(style);
        return style;
    }

    private CellStyle createCenterStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorderThin(style);
        return style;
    }

    private CellStyle createCenterAltStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xEB, (byte) 0xF4, (byte) 0xFF}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorderThin(style);
        return style;
    }

    private CellStyle createLinkStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.BLUE.getIndex());
        font.setUnderline(Font.U_SINGLE);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setBorderThin(style);
        return style;
    }

    private CellStyle createLinkAltStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.BLUE.getIndex());
        font.setUnderline(Font.U_SINGLE);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xEB, (byte) 0xF4, (byte) 0xFF}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorderThin(style);
        return style;
    }

    private void setBorderThin(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
}
