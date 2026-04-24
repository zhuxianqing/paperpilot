package com.paperpilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.paperpilot.dto.AIConfigDTO;
import com.paperpilot.dto.PaperDTO;
import com.paperpilot.dto.request.CreateAnalysisTaskRequest;
import com.paperpilot.dto.response.AnalysisLookupItemVO;
import com.paperpilot.dto.response.AnalysisPaperVO;
import com.paperpilot.dto.response.AnalysisTaskVO;
import com.paperpilot.dto.response.PageResult;
import com.paperpilot.entity.AnalysisTask;
import com.paperpilot.entity.AnalysisTaskPaper;
import com.paperpilot.entity.UserPaperAnalysis;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.mapper.AnalysisTaskMapper;
import com.paperpilot.mapper.AnalysisTaskPaperMapper;
import com.paperpilot.mapper.UserPaperAnalysisMapper;
import com.paperpilot.service.AIService;
import com.paperpilot.service.AnalysisTaskAsyncExecutor;
import com.paperpilot.service.AnalysisTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskServiceImpl implements AnalysisTaskService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisTaskPaperMapper analysisTaskPaperMapper;
    private final UserPaperAnalysisMapper userPaperAnalysisMapper;
    private final AIService aiService;
    private final AnalysisTaskAsyncExecutor analysisTaskAsyncExecutor;

    @Override
    @Transactional
    public AnalysisTaskVO createTask(Long userId, CreateAnalysisTaskRequest request) {
        boolean useUserConfig = Boolean.TRUE.equals(request.getUseUserConfig());
        AIConfigDTO config = request.getConfig();
        List<PaperDTO> papers = request.getPapers();

        Map<String, UserPaperAnalysis> existingMap = loadExistingAnalyses(userId, papers);
        List<PreparedPaper> preparedPapers = new ArrayList<>();
        int newCount = 0;
        int reusedCount = 0;
        int reanalyzeCount = 0;

        for (int index = 0; index < papers.size(); index++) {
            PaperDTO paper = papers.get(index);
            String paperKey = buildPaperKey(paper);
            paper.setPaperKey(paperKey);
            UserPaperAnalysis existing = existingMap.get(paperKey);
            String decision = normalizeDecision(paper, existing);

            PreparedPaper preparedPaper = new PreparedPaper();
            preparedPaper.paper = paper;
            preparedPaper.paperKey = paperKey;
            preparedPaper.displayOrder = index;
            preparedPaper.resultSource = mapResultSource(existing, decision);

            if (existing == null) {
                UserPaperAnalysis created = buildCanonicalAnalysis(userId, paper, paperKey);
                created.setStatus("pending");
                userPaperAnalysisMapper.insert(created);
                preparedPaper.analysis = created;
                newCount++;
            } else {
                preparedPaper.analysis = existing;
                if ("reuse".equals(decision)) {
                    reusedCount++;
                } else {
                    hydrateCanonical(existing, paper, paperKey);
                    existing.setStatus("pending");
                    existing.setErrorMessage(null);
                    existing.setAnalyzedAt(null);
                    userPaperAnalysisMapper.updateById(existing);
                    reanalyzeCount++;
                }
            }
            preparedPapers.add(preparedPaper);
        }

        int aiCount = newCount + reanalyzeCount;
        AnalysisTask task = new AnalysisTask();
        task.setTaskNo(generateTaskNo());
        task.setUserId(userId);
        task.setStatus(aiCount > 0 ? "pending" : "completed");
        task.setTotalCount(papers.size());
        task.setProcessedCount(reusedCount);
        task.setSuccessCount(reusedCount);
        task.setFailedCount(0);
        task.setNewCount(newCount);
        task.setReusedCount(reusedCount);
        task.setReanalyzeCount(reanalyzeCount);
        task.setUseUserConfig(useUserConfig);
        task.setProvider(config != null ? config.getProvider() : null);
        task.setModel(config != null ? config.getModel() : null);
        task.setQuotaReserved(useUserConfig ? 0 : aiCount);
        task.setQuotaConsumed(0);
        if (aiCount == 0) {
            task.setCompletedAt(LocalDateTime.now());
        }
        analysisTaskMapper.insert(task);

        for (PreparedPaper preparedPaper : preparedPapers) {
            AnalysisTaskPaper relation = new AnalysisTaskPaper();
            relation.setAnalysisTaskId(task.getId());
            relation.setUserPaperAnalysisId(preparedPaper.analysis.getId());
            relation.setResultSource(preparedPaper.resultSource);
            relation.setDisplayOrder(preparedPaper.displayOrder);
            analysisTaskPaperMapper.insert(relation);
        }

        if (aiCount > 0) {
            analysisTaskAsyncExecutor.executeTaskAsync(task.getId());
        }
        return toTaskVO(task);
    }

    @Override
    public AnalysisTaskVO getTask(Long userId, String taskNo) {
        return toTaskVO(findTask(userId, taskNo));
    }

    @Override
    public List<AnalysisPaperVO> getTaskPapers(Long userId, String taskNo) {
        AnalysisTask task = findTask(userId, taskNo);
        List<AnalysisTaskPaper> relations = analysisTaskPaperMapper.selectList(new LambdaQueryWrapper<AnalysisTaskPaper>()
                .eq(AnalysisTaskPaper::getAnalysisTaskId, task.getId())
                .orderByAsc(AnalysisTaskPaper::getDisplayOrder)
                .orderByAsc(AnalysisTaskPaper::getId));
        if (relations.isEmpty()) {
            return List.of();
        }

        Map<Long, UserPaperAnalysis> analysisMap = userPaperAnalysisMapper.selectBatchIds(relations.stream()
                        .map(AnalysisTaskPaper::getUserPaperAnalysisId)
                        .collect(Collectors.toSet()))
                .stream()
                .filter(item -> Objects.equals(item.getUserId(), userId))
                .collect(Collectors.toMap(UserPaperAnalysis::getId, item -> item));

        List<AnalysisPaperVO> results = new ArrayList<>();
        for (AnalysisTaskPaper relation : relations) {
            UserPaperAnalysis analysis = analysisMap.get(relation.getUserPaperAnalysisId());
            if (analysis != null) {
                results.add(toPaperVO(analysis, relation));
            }
        }
        return results;
    }

    @Override
    public PageResult<AnalysisTaskVO> getTaskHistory(Long userId, Integer page, Integer size) {
        Page<AnalysisTask> result = analysisTaskMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getUserId, userId)
                .orderByDesc(AnalysisTask::getCreatedAt));
        return PageResult.of(result.getRecords().stream().map(this::toTaskVO).collect(Collectors.toList()), result.getTotal(), page, size);
    }

    @Override
    public PageResult<AnalysisPaperVO> getAnalysisHistory(Long userId, Integer page, Integer size, String sortBy, String order, String keyword) {
        LambdaQueryWrapper<UserPaperAnalysis> wrapper = new LambdaQueryWrapper<UserPaperAnalysis>()
                .eq(UserPaperAnalysis::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(UserPaperAnalysis::getTitle, keyword).or().like(UserPaperAnalysis::getPaperDoi, keyword));
        }
        boolean asc = "asc".equalsIgnoreCase(order);
        if ("citations".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, asc, UserPaperAnalysis::getCitations);
        } else if ("year".equalsIgnoreCase(sortBy)) {
            wrapper.orderBy(true, asc, UserPaperAnalysis::getPublishYear);
        } else {
            wrapper.orderByDesc(UserPaperAnalysis::getUpdatedAt);
        }
        Page<UserPaperAnalysis> result = userPaperAnalysisMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords().stream().map(item -> toPaperVO(item, null)).collect(Collectors.toList()), result.getTotal(), page, size);
    }

    @Override
    public List<AnalysisLookupItemVO> lookupAnalyses(Long userId, List<PaperDTO> papers) {
        if (papers == null || papers.isEmpty()) {
            return List.of();
        }
        Map<String, UserPaperAnalysis> existingMap = loadExistingAnalyses(userId, papers);
        return papers.stream().map(paper -> {
            String paperKey = buildPaperKey(paper);
            UserPaperAnalysis analysis = existingMap.get(paperKey);
            return AnalysisLookupItemVO.builder()
                    .paperKey(paperKey)
                    .paperDoi(normalizeDoi(paper.getDoi()))
                    .analyzed(analysis != null)
                    .analyzedAt(analysis != null ? analysis.getAnalyzedAt() : null)
                    .status(analysis != null ? analysis.getStatus() : null)
                    .build();
        }).collect(Collectors.toList());
    }

    private Map<String, UserPaperAnalysis> loadExistingAnalyses(Long userId, List<PaperDTO> papers) {
        Set<String> paperKeys = new HashSet<>();
        for (PaperDTO paper : papers) {
            paperKeys.add(buildPaperKey(paper));
        }
        if (paperKeys.isEmpty()) {
            return Map.of();
        }
        return userPaperAnalysisMapper.selectList(new LambdaQueryWrapper<UserPaperAnalysis>()
                        .eq(UserPaperAnalysis::getUserId, userId)
                        .in(UserPaperAnalysis::getPaperKey, paperKeys))
                .stream()
                .collect(Collectors.toMap(UserPaperAnalysis::getPaperKey, item -> item, (left, right) -> left));
    }

    private UserPaperAnalysis buildCanonicalAnalysis(Long userId, PaperDTO paper, String paperKey) {
        UserPaperAnalysis analysis = new UserPaperAnalysis();
        analysis.setUserId(userId);
        hydrateCanonical(analysis, paper, paperKey);
        return analysis;
    }

    private void hydrateCanonical(UserPaperAnalysis analysis, PaperDTO paper, String paperKey) {
        analysis.setPaperKey(paperKey);
        analysis.setPaperDoi(normalizeDoi(paper.getDoi()));
        analysis.setTitle(paper.getTitle());
        analysis.setAuthors(paper.getAuthors() == null ? null : paper.getAuthors().stream().filter(Objects::nonNull).collect(Collectors.joining(", ")));
        analysis.setAbstractText(paper.getAbstracts());
        analysis.setJournal(paper.getJournal());
        analysis.setPublishYear(paper.getPublishYear());
        analysis.setCitations(paper.getCitations());
        analysis.setSourcePlatform("extension");
        analysis.setSourceUrl(paper.getSourceUrl());
        analysis.setPdfUrl(paper.getPdfUrl());
    }

    private AnalysisTask findTask(Long userId, String taskNo) {
        AnalysisTask task = analysisTaskMapper.selectOne(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getUserId, userId)
                .eq(AnalysisTask::getTaskNo, taskNo));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在");
        }
        return task;
    }

    private String generateTaskNo() {
        return "AT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String normalizeDoi(String doi) {
        return doi == null ? null : doi.trim().toLowerCase();
    }

    private String buildPaperKey(PaperDTO paper) {
        if (paper.getPaperKey() != null && !paper.getPaperKey().isBlank()) {
            return paper.getPaperKey();
        }
        if (paper.getDoi() != null && !paper.getDoi().isBlank()) {
            return normalizeDoi(paper.getDoi());
        }
        String firstAuthor = paper.getAuthors() == null || paper.getAuthors().isEmpty() ? "" : paper.getAuthors().get(0);
        return aiService.sha256Hash((paper.getTitle() == null ? "" : paper.getTitle().trim().toLowerCase()) + "|" + firstAuthor + "|" + (paper.getPublishYear() == null ? "" : paper.getPublishYear()));
    }

    private String normalizeDecision(PaperDTO paper, UserPaperAnalysis existing) {
        if (existing == null) {
            return "reanalyze";
        }
        if (Boolean.TRUE.equals(paper.getForceReanalyze())) {
            return "reanalyze";
        }
        if ("reanalyze".equalsIgnoreCase(paper.getReuseDecision())) {
            return "reanalyze";
        }
        return "reuse";
    }

    private String mapResultSource(UserPaperAnalysis existing, String decision) {
        if (existing == null) {
            return "new";
        }
        return "reuse".equals(decision) ? "reused" : "reanalyze";
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

    private AnalysisTaskVO toTaskVO(AnalysisTask task) {
        return AnalysisTaskVO.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .processedCount(task.getProcessedCount())
                .successCount(task.getSuccessCount())
                .failedCount(task.getFailedCount())
                .newCount(task.getNewCount())
                .reusedCount(task.getReusedCount())
                .reanalyzeCount(task.getReanalyzeCount())
                .useUserConfig(task.getUseUserConfig())
                .provider(task.getProvider())
                .model(task.getModel())
                .quotaReserved(task.getQuotaReserved())
                .quotaConsumed(task.getQuotaConsumed())
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private AnalysisPaperVO toPaperVO(UserPaperAnalysis analysis, AnalysisTaskPaper relation) {
        return AnalysisPaperVO.builder()
                .id(analysis.getId())
                .paperKey(analysis.getPaperKey())
                .paperDoi(analysis.getPaperDoi())
                .title(analysis.getTitle())
                .titleCn(analysis.getTitleCn())
                .abstractText(analysis.getAbstractText())
                .abstractCn(analysis.getAbstractCn())
                .authors(analysis.getAuthors())
                .journal(analysis.getJournal())
                .publishYear(analysis.getPublishYear())
                .citations(analysis.getCitations())
                .sourcePlatform(analysis.getSourcePlatform())
                .sourceUrl(analysis.getSourceUrl())
                .pdfUrl(analysis.getPdfUrl())
                .summaryZh(analysis.getSummaryZh())
                .keywordsZh(analysis.getKeywordsZh())
                .methodologyZh(analysis.getMethodologyZh())
                .resultSource(relation != null ? relation.getResultSource() : null)
                .displayOrder(relation != null ? relation.getDisplayOrder() : null)
                .conclusionZh(analysis.getConclusionZh())
                .researchFindingsZh(analysis.getResearchFindingsZh())
                .status(analysis.getStatus())
                .errorMessage(analysis.getErrorMessage())
                .analyzedAt(analysis.getAnalyzedAt())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    private static class PreparedPaper {
        private PaperDTO paper;
        private String paperKey;
        private UserPaperAnalysis analysis;
        private String resultSource;
        private Integer displayOrder;
    }
}
