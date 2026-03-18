package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.inbox.InboxItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
public class Big3Controller {

    private final Big3Service big3Service;

    public Big3Controller(Big3Service big3Service) {
        this.big3Service = big3Service;
    }

    @PostMapping("/big3")
    @Operation(summary = "Select today's Big3", description = "Picks up to three inbox items as today's recovery priorities.")
    public ApiResponse<SelectBig3Response> selectBig3(
            @Valid @RequestBody SelectBig3Request request
    ) {
        Big3SelectionResponse selection = big3Service.selectTodayBig3(request.userId(), request.itemIds());
        List<Big3Item> selectedItems = selection.selectedItems().stream()
                .map(Big3Item::from)
                .toList();

        SelectBig3Response response = new SelectBig3Response(
                selection.selectedDate().toString(),
                selection.selectedAt().toString(),
                selectedItems.size(),
                selectedItems
        );
        return ApiResponse.success(response, "BIG3_SELECTED");
    }

    public record SelectBig3Request(
            @NotBlank(message = "userId는 필수입니다.")
            String userId,
            @NotEmpty(message = "itemIds는 최소 1개 이상이어야 합니다.")
            @Size(max = 3, message = "itemIds는 최대 3개까지 허용됩니다.")
            List<@NotBlank(message = "itemId는 비어 있을 수 없습니다.") String> itemIds
    ) {
    }

    public record SelectBig3Response(
            String selectedDate,
            String selectedAt,
            int selectedCount,
            List<Big3Item> selectedItems
    ) {
    }

    public record Big3Item(
            String itemId,
            String content
    ) {
        static Big3Item from(InboxItemResponse item) {
            return new Big3Item(item.id(), item.content());
        }
    }
}
