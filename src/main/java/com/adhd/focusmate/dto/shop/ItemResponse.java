package com.adhd.focusmate.dto.shop;

import com.adhd.focusmate.domain.model.Item;
import com.adhd.focusmate.domain.model.type.ItemType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이템 응답 DTO")
public record ItemResponse(
        @Schema(description = "아이템 ID", example = "1") Long id,

        @Schema(description = "아이템 이름", example = "면제권") String name,

        @Schema(description = "아이템 설명", example = "챌린지 실패 시 예치금을 보호합니다") String description,

        @Schema(description = "아이템 타입", example = "PASS_TICKET") ItemType itemType,

        @Schema(description = "가격 (포인트)", example = "500") Integer price) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getItemType(),
                item.getPrice());
    }
}
