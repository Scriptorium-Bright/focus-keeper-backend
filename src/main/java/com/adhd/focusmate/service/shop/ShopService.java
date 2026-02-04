package com.adhd.focusmate.service.shop;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.model.Item;
import com.adhd.focusmate.domain.model.UserItem;
import com.adhd.focusmate.domain.model.Wallet;
import com.adhd.focusmate.dto.shop.BuyItemRequest;
import com.adhd.focusmate.dto.shop.BuyItemResponse;
import com.adhd.focusmate.dto.shop.ItemResponse;
import com.adhd.focusmate.repository.ItemRepository;
import com.adhd.focusmate.repository.UserItemRepository;
import com.adhd.focusmate.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 아이템 상점 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final WalletRepository walletRepository;

    /**
     * 모든 판매 중인 아이템 조회
     */
    @Transactional(readOnly = true)
    public List<ItemResponse> getAllItems() {
        return itemRepository.findAll().stream()
                .filter(Item::getActive)
                .map(ItemResponse::from)
                .toList();
    }

    /**
     * 아이템 구매
     * 1. 아이템 존재 확인
     * 2. 포인트 차감
     * 3. 인벤토리에 추가
     * 하드코딩된 에러 메시지들을 전부 모아주는 방향도 고려
     */
    @Transactional
    public BuyItemResponse buyItem(Long userId, BuyItemRequest request) {
        // 1. 아이템 조회
        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Item not found"));

        if (!item.getActive()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Item is not available");
        }

        // 2. 총 가격 계산
        long totalCost = (long) item.getPrice() * request.quantity();

        // 3. Wallet 조회 및 포인트 차감
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Wallet not found"));

        wallet.subtractPoint(totalCost);

        // 4. 인벤토리에 추가
        UserItem userItem = userItemRepository.findByUserIdAndItemId(userId, item.getId())
                .orElseGet(() -> UserItem.builder()
                        .user(wallet.getUser())
                        .item(item)
                        .quantity(0)
                        .build());

        userItem.addQuantity(request.quantity());
        userItemRepository.save(userItem);

        log.info("User [{}] bought {} x {} for {} points",
                userId, item.getName(), request.quantity(), totalCost);

        return BuyItemResponse.success(item.getId(), request.quantity(), wallet.getPoint());
    }
}
