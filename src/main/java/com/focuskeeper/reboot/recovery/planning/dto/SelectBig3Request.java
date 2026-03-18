package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SelectBig3Request(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotEmpty(message = "itemIds는 최소 1개 이상이어야 합니다.")
        @Size(max = 3, message = "itemIds는 최대 3개까지 허용됩니다.")
        List<@NotBlank(message = "itemId는 비어 있을 수 없습니다.") String> itemIds
) {
}
