package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateExecutionUnitRequest (
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "big3ItemId는 필수입니다.")
        String big3ItemId,
        @NotEmpty(message = "title은 최소 1개 이상이어야 합니다.")
        @Size(max = 5, message = "title은 최대 5개까지 허용됩니다.")
        List<
                @NotBlank(message = "title은 비어 있을 수 없습니다.")
                @Size(max = 200, message = "title은 최대 200자까지 허용됩니다.")
                String
        > title
) {
}
