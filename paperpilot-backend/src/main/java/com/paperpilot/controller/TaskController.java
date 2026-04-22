package com.paperpilot.controller;

import com.paperpilot.dto.request.SearchTaskRequest;
import com.paperpilot.dto.response.PageResult;
import com.paperpilot.dto.response.TaskVO;
import com.paperpilot.service.TaskService;
import com.paperpilot.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/search")
    public Result<TaskVO> createTask(@AuthenticationPrincipal UserDetails user,
                                     @RequestBody @Valid SearchTaskRequest request) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(taskService.createTask(userId, request));
    }

    @GetMapping("/{taskNo}")
    public Result<TaskVO> getTask(@AuthenticationPrincipal UserDetails user,
                                  @PathVariable String taskNo) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(taskService.getTask(userId, taskNo));
    }

    @GetMapping("/list")
    public Result<PageResult<TaskVO>> getTaskList(@AuthenticationPrincipal UserDetails user,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer size) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(taskService.getTaskList(userId, page, size));
    }

    @GetMapping("/{taskNo}/papers")
    public Result<List<TaskVO.PaperVO>> getTaskPapers(@AuthenticationPrincipal UserDetails user,
                                                      @PathVariable String taskNo) {
        Long userId = Long.valueOf(user.getUsername());
        return Result.success(taskService.getTaskPapers(userId, taskNo));
    }

    @GetMapping("/{taskNo}/download")
    public Result<String> downloadTask(@AuthenticationPrincipal UserDetails user,
                                        @PathVariable String taskNo) {
        Long userId = Long.valueOf(user.getUsername());
        String downloadUrl = taskService.downloadTask(userId, taskNo);
        return Result.success(downloadUrl);
    }
}
