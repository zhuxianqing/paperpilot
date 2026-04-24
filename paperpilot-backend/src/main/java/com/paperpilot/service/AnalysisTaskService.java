package com.paperpilot.service;

import com.paperpilot.dto.PaperDTO;
import com.paperpilot.dto.request.CreateAnalysisTaskRequest;
import com.paperpilot.dto.response.AnalysisLookupItemVO;
import com.paperpilot.dto.response.AnalysisPaperVO;
import com.paperpilot.dto.response.AnalysisTaskVO;
import com.paperpilot.dto.response.PageResult;

import java.util.List;

public interface AnalysisTaskService {

    AnalysisTaskVO createTask(Long userId, CreateAnalysisTaskRequest request);

    AnalysisTaskVO getTask(Long userId, String taskNo);

    List<AnalysisPaperVO> getTaskPapers(Long userId, String taskNo);

    PageResult<AnalysisTaskVO> getTaskHistory(Long userId, Integer page, Integer size);

    PageResult<AnalysisPaperVO> getAnalysisHistory(Long userId, Integer page, Integer size, String sortBy, String order, String keyword);

    List<AnalysisLookupItemVO> lookupAnalyses(Long userId, List<PaperDTO> papers);
}
