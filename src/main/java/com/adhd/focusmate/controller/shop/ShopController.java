package com.adhd.focusmate.controller.shop;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.dto.shop.BuyItemRequest;
import com.adhd.focusmate.dto.shop.BuyItemResponse;
import com.adhd.focusmate.dto.shop.ItemResponse;
import com.adhd.focusmate.service.shop.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Shop", description = "아이템 상점 API")
@RestController
@RequestMapping("/api/v1/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    @Operation(summary = "아이템 목록 조회", description = "구매 가능한 모든 아이템 목록을 반환합니다.")
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getAllItems() {
        List<ItemResponse> items = shopService.getAllItems();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @Operation(summary = "아이템 구매", description = "포인트를 사용하여 아이템을 구매합니다. 구매한 아이템은 인벤토리에 추가됩니다.")
    @PostMapping("/buy")
    public ResponseEntity<ApiResponse<BuyItemResponse>> buyItem(
            @Parameter(description = "사용자 ID (임시 - OAuth 구현 후 토큰에서 추출)", required = true) @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody BuyItemRequest request) {
        BuyItemResponse response = shopService.buyItem(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
