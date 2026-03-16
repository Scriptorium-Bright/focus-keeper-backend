package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery")
public class TimeboxController {

    private final TimeboxService timeboxService;

    public TimeboxController(TimeboxService timeboxService) {
        this.timeboxService = timeboxService;
    }

    @PostMapping("/timeboxes")
    public ApiResponse<AllocateTimeboxesResponse> allocateTimeboxes(
            @Valid @RequestBody AllocateTimeboxesRequest request
    ) {
        List<TimeboxService.TimeboxCommand> commands = request.timeboxes().stream()
                .map(timebox -> new TimeboxService.TimeboxCommand(
                        timebox.itemId(),
                        timebox.startAt(),
                        timebox.endAt(),
                        timebox.firstRecoveryBlock()
                ))
                .toList();

        List<Timebox> allocatedTimeboxes = timeboxService.allocateTimeboxes(request.userId(), commands);
        List<AllocatedTimebox> responseItems = allocatedTimeboxes.stream()
                .map(timebox -> new AllocatedTimebox(
                        timebox.id(),
                        timebox.itemId(),
                        timebox.itemContent(),
                        timebox.startAt().toString(),
                        timebox.endAt().toString(),
                        timebox.firstRecoveryBlock(),
                        timebox.createdAt().toString()
                ))
                .toList();

        String plannedDate = allocatedTimeboxes.getFirst().startAt().toLocalDate().toString();
        AllocateTimeboxesResponse response = new AllocateTimeboxesResponse(
                plannedDate,
                responseItems.size(),
                responseItems
        );
        return ApiResponse.success(response, "TIMEBOXES_ALLOCATED");
    }

    public record AllocateTimeboxesRequest(
            @NotBlank(message = "userId는 필수입니다.")
            String userId,
            @NotEmpty(message = "timeboxes는 최소 1개 이상이어야 합니다.")
            @Size(max = 3, message = "timeboxes는 최대 3개까지 허용됩니다.")
            List<@Valid TimeboxPayload> timeboxes
    ) {
    }

    public record TimeboxPayload(
            @NotBlank(message = "itemId는 비어 있을 수 없습니다.")
            String itemId,
            @NotBlank(message = "startAt은 비어 있을 수 없습니다.")
            String startAt,
            @NotBlank(message = "endAt은 비어 있을 수 없습니다.")
            String endAt,
            boolean firstRecoveryBlock
    ) {
    }

    public record AllocateTimeboxesResponse(
            String plannedDate,
            int allocatedCount,
            List<AllocatedTimebox> timeboxes
    ) {
    }

    public record AllocatedTimebox(
            String timeboxId,
            String itemId,
            String content,
            String startAt,
            String endAt,
            boolean firstRecoveryBlock,
            String createdAt
    ) {
    }
}
