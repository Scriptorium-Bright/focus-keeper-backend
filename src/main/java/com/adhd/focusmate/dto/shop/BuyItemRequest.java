package com.adhd.focusmate.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "아이템 구매 요청 DTO")
public record BuyItemRequest(
        @Schema(description = "아이템 ID", example = "1", required = true) @NotNull(message = "아이템 ID는 필수입니다") Long itemId,

        @Schema(description = "구매 수량", example = "1", required = true) @NotNull(message = "수량은 필수입니다") @Min(value = 1, message = "수량은 1 이상이어야 합니다") Integer quantity) {
}
