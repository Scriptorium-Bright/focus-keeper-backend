package com.adhd.focusmate.controller.ai;

import com.adhd.focusmate.dto.ai.TaskChunkResponse;
import com.adhd.focusmate.dto.ai.TaskSplitRequest;
import com.adhd.focusmate.service.ai.TaskAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Task Tools", description = "AI-powered task management tools")
@RestController
@RequestMapping("/api/v1/ai/tasks")
@RequiredArgsConstructor
public class TaskAiController {

    private final TaskAiService taskAiService;

    @Operation(summary = "Split Task (Goblin Tool)", description = "Breaks down a task into smaller steps based on energy level.")
    @PostMapping("/split")
    public ResponseEntity<TaskChunkResponse> splitTask(@RequestBody TaskSplitRequest request) {
        return ResponseEntity.ok(taskAiService.splitTask(request));
    }
}
