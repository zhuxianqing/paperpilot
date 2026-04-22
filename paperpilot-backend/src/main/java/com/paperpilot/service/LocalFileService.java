package com.paperpilot.service;

import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class LocalFileService {

    @Value("${export.local-path:./exports}")
    private String exportPath;

    @Value("${export.expiry-days:7}")
    private int expiryDays;

    private Path basePath;

    @PostConstruct
    public void init() {
        try {
            basePath = Paths.get(exportPath).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
            log.info("Local export storage initialized at: {}", basePath);
        } catch (IOException e) {
            log.error("Failed to initialize export directory", e);
            throw new RuntimeException("Failed to initialize export directory", e);
        }
    }

    /**
     * 保存导出文件
     */
    public String saveFile(byte[] content, String extension, Long userId) {
        try {
            String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            String fileName = String.format("user_%d_%d.%s", userId, System.currentTimeMillis(), extension);

            Path dateDir = basePath.resolve(date);
            Files.createDirectories(dateDir);

            Path filePath = dateDir.resolve(fileName);
            Files.write(filePath, content);

            // 返回相对路径作为文件ID
            String fileId = date + "/" + fileName;
            log.info("File saved: {}", fileId);

            return fileId;

        } catch (IOException e) {
            log.error("Failed to save export file", e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }
    }

    /**
     * 下载文件到响应流
     */
    public void downloadFile(String fileId, HttpServletResponse response, String downloadName) {
        try {
            Path filePath = resolveFilePath(fileId);

            if (!Files.exists(filePath)) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }

            String encodedFileName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + downloadName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("X-File-Name", encodedFileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition,Content-Length,X-File-Name");
            response.setContentLengthLong(Files.size(filePath));

            // 流式传输文件
            try (InputStream in = Files.newInputStream(filePath);
                 OutputStream out = response.getOutputStream()) {
                StreamUtils.copy(in, out);
            }

            log.info("File downloaded: {}", fileId);

        } catch (IOException e) {
            log.error("Failed to download file: {}", fileId, e);
            throw new BusinessException(ErrorCode.EXPORT_FAILED);
        }
    }

    /**
     * 删除过期文件
     */
    public void cleanupExpiredFiles() {
        try {
            LocalDate expiryDate = LocalDate.now().minusDays(expiryDays);
            Path expiryDir = basePath.resolve(expiryDate.format(DateTimeFormatter.ISO_DATE));

            if (Files.exists(expiryDir)) {
                deleteDirectoryRecursively(expiryDir);
                log.info("Cleaned up expired export files for date: {}", expiryDate);
            }
        } catch (IOException e) {
            log.error("Failed to cleanup expired files", e);
        }
    }

    /**
     * 获取文件过期时间
     */
    public LocalDateTime getExpiryTime(String fileId) {
        // 从文件路径解析日期
        String[] parts = fileId.split("/");
        if (parts.length > 0) {
            try {
                LocalDate fileDate = LocalDate.parse(parts[0], DateTimeFormatter.ISO_DATE);
                return fileDate.plusDays(expiryDays).atStartOfDay();
            } catch (Exception e) {
                log.warn("Failed to parse file date from fileId: {}", fileId);
            }
        }
        // 默认7天后过期
        return LocalDateTime.now().plusDays(expiryDays);
    }

    /**
     * 解析并验证文件路径（防止目录遍历攻击）
     */
    private Path resolveFilePath(String fileId) {
        Path filePath = basePath.resolve(fileId).normalize();

        // 安全检查：确保文件路径在基础目录内
        if (!filePath.startsWith(basePath)) {
            log.warn("Invalid file path detected (directory traversal attempt): {}", fileId);
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }

        return filePath;
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                entries.forEach(entry -> {
                    try {
                        deleteDirectoryRecursively(entry);
                    } catch (IOException e) {
                        log.error("Failed to delete: {}", entry, e);
                    }
                });
            }
        }
        Files.delete(path);
    }
}
