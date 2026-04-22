package com.paperpilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperpilot.dto.request.SearchTaskRequest;
import com.paperpilot.dto.response.PageResult;
import com.paperpilot.dto.response.TaskVO;
import com.paperpilot.entity.Paper;
import com.paperpilot.entity.PaperTask;
import com.paperpilot.exception.BusinessException;
import com.paperpilot.exception.ErrorCode;
import com.paperpilot.mapper.PaperMapper;
import com.paperpilot.mapper.PaperTaskMapper;
import com.paperpilot.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final PaperTaskMapper taskMapper;
    private final PaperMapper paperMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TaskVO createTask(Long userId, SearchTaskRequest request) {
        // 创建任务
        PaperTask task = new PaperTask();
        task.setTaskNo(request.getTaskNo());
        task.setUserId(userId);
        task.setSource(request.getSource());
        task.setKeyword(request.getKeyword());
        task.setStatus("completed");
        task.setTotalCount(request.getPapers().size());
        task.setProcessedCount(request.getPapers().size());
        task.setQuotaConsumed(request.getPapers().size());

        // 序列化filters
        if (request.getFilters() != null) {
            try {
                task.setFilters(objectMapper.writeValueAsString(request.getFilters()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize filters", e);
            }
        }

        task.setStartedAt(LocalDateTime.now());
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // 保存文献
        List<SearchTaskRequest.PaperItem> paperItems = request.getPapers();
        for (SearchTaskRequest.PaperItem item : paperItems) {
            Paper paper = new Paper();
            paper.setTaskId(task.getId());
            paper.setDoi(item.getDoi());
            paper.setTitle(item.getTitle());
            try {
                paper.setAuthors(objectMapper.writeValueAsString(item.getAuthors()));
            } catch (JsonProcessingException e) {
                paper.setAuthors("[]");
            }
            paper.setAbstracts(item.getAbstractText());
            paper.setJournal(item.getJournal());
            paper.setPublishYear(item.getPublishYear());
            if (item.getImpactFactor() != null) {
                paper.setImpactFactor(new java.math.BigDecimal(item.getImpactFactor().toString()));
            }
            paper.setQuartile(item.getQuartile());
            paper.setCitations(item.getCitations() != null ? item.getCitations() : 0);
            paper.setPdfUrl(item.getPdfUrl());
            paper.setSourceUrl(item.getSourceUrl());
            paperMapper.insert(paper);
        }

        return convertToVO(task, null);
    }

    @Override
    public TaskVO getTask(Long userId, String taskNo) {
        PaperTask task = findTaskByNo(taskNo, userId);
        return convertToVO(task, null);
    }

    @Override
    public PageResult<TaskVO> getTaskList(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<PaperTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperTask::getUserId, userId)
                .orderByDesc(PaperTask::getCreatedAt);

        Page<PaperTask> pageResult = taskMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<TaskVO> records = pageResult.getRecords().stream()
                .map(t -> convertToVO(t, null))
                .collect(Collectors.toList());

        return PageResult.of(records, pageResult.getTotal(), page, size);
    }

    @Override
    public List<TaskVO.PaperVO> getTaskPapers(Long userId, String taskNo) {
        PaperTask task = findTaskByNo(taskNo, userId);

        LambdaQueryWrapper<Paper> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Paper::getTaskId, task.getId());
        List<Paper> papers = paperMapper.selectList(wrapper);

        return papers.stream()
                .map(this::convertPaperToVO)
                .collect(Collectors.toList());
    }

    @Override
    public String downloadTask(Long userId, String taskNo) {
        PaperTask task = findTaskByNo(taskNo, userId);
        return task.getResultFileUrl();
    }

    private PaperTask findTaskByNo(String taskNo, Long userId) {
        LambdaQueryWrapper<PaperTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperTask::getTaskNo, taskNo)
                .eq(PaperTask::getUserId, userId);
        PaperTask task = taskMapper.selectOne(wrapper);

        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private TaskVO convertToVO(PaperTask task, List<TaskVO.PaperVO> papers) {
        Map<String, Object> filters = null;
        if (task.getFilters() != null) {
            try {
                filters = objectMapper.readValue(task.getFilters(),
                        new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize filters", e);
            }
        }

        return TaskVO.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .source(task.getSource())
                .keyword(task.getKeyword())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .processedCount(task.getProcessedCount())
                .quotaConsumed(task.getQuotaConsumed())
                .filters(filters)
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private TaskVO.PaperVO convertPaperToVO(Paper paper) {
        List<String> authors = new ArrayList<>();
        if (paper.getAuthors() != null) {
            try {
                authors = objectMapper.readValue(paper.getAuthors(),
                        new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize authors", e);
            }
        }

        List<String> aiKeywords = new ArrayList<>();
        if (paper.getAiKeywords() != null) {
            try {
                aiKeywords = objectMapper.readValue(paper.getAiKeywords(),
                        new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize aiKeywords", e);
            }
        }

        return TaskVO.PaperVO.builder()
                .id(paper.getId())
                .doi(paper.getDoi())
                .title(paper.getTitle())
                .authors(authors)
                .abstractText(paper.getAbstracts())
                .journal(paper.getJournal())
                .publishYear(paper.getPublishYear())
                .impactFactor(paper.getImpactFactor() != null ?
                        paper.getImpactFactor().doubleValue() : null)
                .quartile(paper.getQuartile())
                .citations(paper.getCitations())
                .pdfUrl(paper.getPdfUrl())
                .sourceUrl(paper.getSourceUrl())
                .aiSummary(paper.getAiSummary())
                .aiKeywords(aiKeywords)
                .methodology(paper.getMethodology())
                .conclusion(paper.getConclusion())
                .researchFindings(paper.getResearchFindings())
                .build();
    }
}
