package com.paperpilot.task;

import com.paperpilot.service.LocalFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportCleanupTask {

    private final LocalFileService localFileService;

    /**
     * 每天凌晨2点清理过期文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanup() {
        log.info("Starting scheduled export file cleanup");
        localFileService.cleanupExpiredFiles();
        log.info("Scheduled export file cleanup completed");
    }

    /**
     * 应用启动时清理过期文件
     */
    @PostConstruct
    public void startupCleanup() {
        log.info("Starting startup export file cleanup");
        localFileService.cleanupExpiredFiles();
        log.info("Startup export file cleanup completed");
    }
}
