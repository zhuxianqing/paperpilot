package com.paperpilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperpilot.dto.PaperDTO;
import com.paperpilot.entity.AnalysisTask;
import com.paperpilot.entity.AnalysisTaskPaper;
import com.paperpilot.entity.UserPaperAnalysis;
import com.paperpilot.mapper.AnalysisTaskMapper;
import com.paperpilot.mapper.AnalysisTaskPaperMapper;
import com.paperpilot.mapper.UserPaperAnalysisMapper;
import com.paperpilot.service.AIService;
import com.paperpilot.service.AnalysisTaskAsyncExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskAsyncExecutorImpl implements AnalysisTaskAsyncExecutor {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisTaskPaperMapper analysisTaskPaperMapper;
    private final UserPaperAnalysisMapper userPaperAnalysisMapper;
    private final AIService aiService;

    @Override
    @Async("analysisTaskExecutor")
    @Transactional
    public void executeTaskAsync(Long analysisTaskId) {
        AnalysisTask task = analysisTaskMapper.selectById(analysisTaskId);
        if (task == null) {
            return;
        }

        task.setStatus("processing");
        task.setStartedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);

        List<AnalysisTaskPaper> relations = analysisTaskPaperMapper.selectList(new LambdaQueryWrapper<AnalysisTaskPaper>()
                .eq(AnalysisTaskPaper::getAnalysisTaskId, analysisTaskId)
                .in(AnalysisTaskPaper::getResultSource, List.of("new", "reanalyze"))
                .orderByAsc(AnalysisTaskPaper::getDisplayOrder)
                .orderByAsc(AnalysisTaskPaper::getId));

        int successCount = task.getReusedCount() == null ? 0 : task.getReusedCount();
        int failedCount = 0;
        int processedCount = task.getReusedCount() == null ? 0 : task.getReusedCount();
        int quotaConsumed = 0;

        for (AnalysisTaskPaper relation : relations) {
            UserPaperAnalysis analysis = userPaperAnalysisMapper.selectById(relation.getUserPaperAnalysisId());
            if (analysis == null) {
                failedCount++;
                processedCount++;
                continue;
            }

            analysis.setStatus("processing");
            analysis.setErrorMessage(null);
            userPaperAnalysisMapper.updateById(analysis);

            try {
                PaperDTO paper = toPaperDTO(analysis);
                var result = aiService.analyzeSingleWithTranslations(
                        task.getUserId(),
                        paper,
                        Boolean.TRUE.equals(task.getUseUserConfig()),
                        task.getProvider(),
                        task.getModel()
                );
                analysis.setTitleCn(result.getTitleCn());
                analysis.setAbstractCn(result.getAbstractCn());
                analysis.setSummaryZh(result.getSummaryZh());
                analysis.setKeywordsZh(result.getAiKeywords() == null ? null : String.join(", ", result.getAiKeywords()));
                analysis.setMethodologyZh(result.getMethodologyZh());
                analysis.setConclusionZh(result.getConclusionZh());
                analysis.setResearchFindingsZh(result.getResearchFindingsZh());
                analysis.setStatus("completed");
                analysis.setAnalyzedAt(LocalDateTime.now());
                analysis.setErrorMessage(null);
                userPaperAnalysisMapper.updateById(analysis);
                successCount++;
                if (!Boolean.TRUE.equals(task.getUseUserConfig())) {
                    quotaConsumed++;
                }
            } catch (Exception e) {
                log.error("Failed to analyze paper: {}", analysis.getTitle(), e);
                analysis.setStatus("failed");
                analysis.setErrorMessage(e.getMessage());
                userPaperAnalysisMapper.updateById(analysis);
                failedCount++;
            }

            processedCount++;
            task.setProcessedCount(processedCount);
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setQuotaConsumed(quotaConsumed);
            analysisTaskMapper.updateById(task);
        }

        task.setCompletedAt(LocalDateTime.now());
        task.setQuotaConsumed(quotaConsumed);
        if (failedCount == 0) {
            task.setStatus("completed");
        } else if (successCount == 0) {
            task.setStatus("failed");
            task.setErrorMessage("所有论文分析失败");
        } else {
            task.setStatus("partial_failed");
            task.setErrorMessage("部分论文分析失败");
        }
        analysisTaskMapper.updateById(task);
    }

    private PaperDTO toPaperDTO(UserPaperAnalysis analysis) {
        PaperDTO paper = new PaperDTO();
        paper.setDoi(analysis.getPaperDoi());
        paper.setTitle(analysis.getTitle());
        paper.setAuthors(analysis.getAuthors() == null || analysis.getAuthors().isBlank()
                ? List.of()
                : List.of(analysis.getAuthors().split(",\\s*")));
        paper.setAbstracts(analysis.getAbstractText());
        paper.setJournal(analysis.getJournal());
        paper.setPublishYear(analysis.getPublishYear());
        paper.setCitations(analysis.getCitations());
        paper.setSourceUrl(analysis.getSourceUrl());
        paper.setPdfUrl(analysis.getPdfUrl());
        paper.setPaperKey(analysis.getPaperKey());
        return paper;
    }
}
