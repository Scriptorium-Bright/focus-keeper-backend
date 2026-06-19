package com.focuskeeper.reboot.recovery.planning.controller;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.planning.dto.*;
import com.focuskeeper.reboot.recovery.planning.service.TimeboxCommand;
import com.focuskeeper.reboot.recovery.planning.service.TimeboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
/**
 * Big3 항목을 실제 timebox 일정으로 배정하는 API를 노출하는 컨트롤러다.
 */
public class TimeboxController {

    private final TimeboxService timeboxService;

    public TimeboxController(TimeboxService timeboxService) {
        this.timeboxService = timeboxService;
    }

    /**
     * 요청받은 timebox 목록을 검증하고 저장한 뒤 배정 결과를 반환한다.
     */
    @PostMapping("/timeboxes")
    @Operation(summary = "Allocate recovery timeboxes", description = "Assigns daily timeboxes and requires exactly one first recovery block.")
    public ApiResponse<AllocateTimeboxesResponse> allocateTimeboxes(
            @Valid @RequestBody AllocateTimeboxesRequest request
    ) {
        List<TimeboxCommand> commands = request.timeboxes().stream()
                .map(timebox -> new TimeboxCommand(
                        timebox.executionUnitId(),
                        timebox.startAt(),
                        timebox.endAt(),
                        timebox.firstRecoveryBlock(),
                        timebox.type()
                ))
                .toList();

        List<TimeboxResponse> allocatedTimeboxes = timeboxService.allocateTimeboxes(request.userId(), commands);
        List<AllocatedTimeboxResponse> responseItems = allocatedTimeboxes.stream()
                .map(timebox -> new AllocatedTimeboxResponse(
                        timebox.timeboxId(),
                        timebox.executionUnitId(),
                        timebox.content(),
                        timebox.startAt(),
                        timebox.endAt(),
                        timebox.firstRecoveryBlock(),
                        timebox.type(),
                        timebox.createdAt()
                ))
                .toList();

        String plannedDate = OffsetDateTime.parse(allocatedTimeboxes.getFirst().startAt()).toLocalDate().toString();
        AllocateTimeboxesResponse response = new AllocateTimeboxesResponse(
                plannedDate,
                responseItems.size(),
                responseItems
        );
        return ApiResponse.success(response, "TIMEBOXES_ALLOCATED");
    }

    @PostMapping("/cancelled")
    public ApiResponse<String> cancelledTimeboxes(@RequestBody TimeboxCancelledRequest request) {
        timeboxService.cancelledTimeBoxesByUser(
                request.ids(),
                request.userId()
        );

        return ApiResponse.success("timebox cancelled by user");
    }
}
