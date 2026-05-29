package com.focuskeeper.reboot.recovery.planning.controller;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.planning.dto.CompleteExecutionUnitRequest;
import com.focuskeeper.reboot.recovery.planning.dto.CreateExecutionUnitRequest;
import com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse;
import com.focuskeeper.reboot.recovery.planning.dto.UpdateExecutionUnitRequest;
import com.focuskeeper.reboot.recovery.planning.service.ExecutionUnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/recovery/execution-units")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
/**
 * Big3 하위 실행 단위 API를 노출하는 컨트롤러다.
 */
public class ExecutionUnitController {

    private final ExecutionUnitService executionUnitService;

    public ExecutionUnitController(ExecutionUnitService executionUnitService) {
        this.executionUnitService = executionUnitService;
    }

    @PostMapping
    @Operation(summary = "Create execution unit", description = "Creates a concrete unit under a selected Big3 item.")
    public ApiResponse<List<ExecutionUnitResponse>> createUnit(
            @Valid @RequestBody CreateExecutionUnitRequest request
    ) {
        List<ExecutionUnitResponse> response = executionUnitService.createUnit(
                request.userId(),
                request.big3SelectionItemId(),
                request.title()
        );
        return ApiResponse.success(response, "EXECUTION_UNIT_CREATED");
    }

    @PatchMapping("/{executionUnitId}")
    @Operation(summary = "Update execution unit", description = "Renames a concrete execution unit.")
    public ApiResponse<ExecutionUnitResponse> updateUnit(
            @PathVariable String executionUnitId,
            @Valid @RequestBody UpdateExecutionUnitRequest request
    ) {
        ExecutionUnitResponse response = executionUnitService.updateUnit(
                request.userId(),
                executionUnitId,
                request.title()
        );
        return ApiResponse.success(response, "EXECUTION_UNIT_UPDATED");
    }

    @PostMapping("/{executionUnitId}/complete")
    @Operation(summary = "Complete execution unit", description = "Marks an execution unit as completed without changing session state.")
    public ApiResponse<ExecutionUnitResponse> completeUnit(
            @PathVariable String executionUnitId,
            @Valid @RequestBody CompleteExecutionUnitRequest request
    ) {
        ExecutionUnitResponse response = executionUnitService.completeUnit(request.userId(), executionUnitId);
        return ApiResponse.success(response, "EXECUTION_UNIT_COMPLETED");
    }
}
