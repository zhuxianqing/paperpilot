package com.paperpilot.service;

import com.paperpilot.dto.request.SearchTaskRequest;
import com.paperpilot.dto.response.PageResult;
import com.paperpilot.dto.response.TaskVO;

import java.util.List;

public interface TaskService {

    TaskVO createTask(Long userId, SearchTaskRequest request);

    TaskVO getTask(Long userId, String taskNo);

    PageResult<TaskVO> getTaskList(Long userId, Integer page, Integer size);

    List<TaskVO.PaperVO> getTaskPapers(Long userId, String taskNo);

    String downloadTask(Long userId, String taskNo);
}
