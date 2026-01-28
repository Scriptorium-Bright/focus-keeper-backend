package com.adhd.focusmate.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이템 구매 결과 DTO")
public record BuyItemResponse(
        @Schema(description = "성공 여부") boolean success,

        @Schema(description = "메시지", example = "아이템 구매 완료") String message,

        @Schema(description = "구매한 아이템 ID") Long itemId,

        @Schema(description = "구매 수량") Integer quantity,

        @Schema(description = "남은 포인트") Long remainingPoints) {
    public static BuyItemResponse success(Long itemId, Integer quantity, Long remainingPoints) {
        return new BuyItemResponse(true, "아이템 구매 완료", itemId, quantity, remainingPoints);
    }
}
