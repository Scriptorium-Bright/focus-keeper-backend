package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.dto.Big3SelectionResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class Big3Service {

    private final InboxItemRepository inboxItemRepository;
    private final Big3SelectionRepository big3SelectionRepository;

    public Big3Service(
            InboxItemRepository inboxItemRepository,
            Big3SelectionRepository big3SelectionRepository
    ) {
        this.inboxItemRepository = inboxItemRepository;
        this.big3SelectionRepository = big3SelectionRepository;
    }

    /**
     *
     * @param userId
     * @param itemIds
     * @return 사용자가 선택한 여러 Brain Dump중 Big 3 (1~3개) 로 최소화
     */
    @Transactional
    public Big3SelectionResponse selectTodayBig3(String userId, List<String> itemIds) {
        List<String> uniqueItemIds = deduplicate(itemIds);

        if (uniqueItemIds.size() != itemIds.size()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("itemIds", "중복된 itemId는 허용되지 않습니다.")
            );
        }

        List<InboxItem> selectedItems = findInboxItemsInRequestOrder(userId, uniqueItemIds);

        // Q. 가져온 Big3를 통해, 만약 유실이 일어났을 경우에 대비해 진행하는 로직인가? 어떤 로직인지에 대한 설명을 주석으로 제시해줬으면 좋겠음
        // A. "유실 복구"보다는, 요청한 itemIds 중 실제로 조회되지 않은 ID를 찾는 방어 로직이다.
        // A. 다른 사용자 itemId, 이미 삭제된 itemId, 존재하지 않는 itemId를 missingItemIds로 돌려주기 위한 검증이다.
        // RQ. 어차피 전역에러를 보내면 missingItemIds가 갖게 되는게 의미가 없지 않나?
        // A. 전역 에러로 보내더라도 details에 missingItemIds가 실리면 클라이언트와 디버깅에서 "무엇이 빠졌는지"를 바로 알 수 있다.
        // A. 즉 상태코드는 공통 처리하고, 도메인 맥락은 details로 남기는 용도라서 완전히 무의미하진 않다.
        if (selectedItems.size() != uniqueItemIds.size()) {
            Set<String> selectedItemIds = selectedItems.stream()
                    .map(InboxItem::getId)
                    .collect(HashSet::new, Set::add, Set::addAll);
            List<String> missingItemIds = uniqueItemIds.stream()
                    .filter(id -> !selectedItemIds.contains(id))
                    .toList();

            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of("missingItemIds", missingItemIds)
            );
        }

        // Q. 수많은 Brain Dump에서 Selection 하는 과정이 없는거같음, 내가 못 보고있는 거일 수도 있지만, 아무래도 처리를 했다면 Client에서 Brain Dump중 1~3개를 선택하여 가져온 것이
        // 저 itemIds라고 생각이 됨, if문에서 size 검증하는 과정을 하나의 로직으로 빼는 것도 좋을거같음 List를 넘겨받고
        // A. 맞다. selection 자체는 클라이언트가 Brain Dump 중 1~3개를 골라 itemIds로 보내는 흐름을 전제로 한다.
        // A. 개수 상한은 요청 DTO에서, 서비스는 중복 제거/존재 여부 확인/오늘자 선택 저장을 맡는 구조다.
        LocalDate selectedDate = LocalDate.now();
        OffsetDateTime selectedAt = OffsetDateTime.now();
        Big3Selection selection = big3SelectionRepository.findByUserIdAndSelectedDate(userId, selectedDate)
                .orElseGet(() -> Big3Selection.create(userId, selectedDate, selectedAt));
        selection.replaceItems(selectedItems, selectedAt);

        return big3SelectionRepository.save(selection).toResponse();
    }

    // Q. orThrow ? 명칭이 조금 이상함, 오늘의 Big3 Task를 가져온다는 거에 있어서는 이견이 없지만, orThrow를 던진다에 대해서는 좀, 메서드 이름에 대한 논의가 필요함
    // A. 네이밍은 requireTodayBig3 정도가 더 의도가 선명할 수 있다. 지금 이름은 "없으면 예외"를 드러내려는 선택이다.
    // Q. 해당 에러들은 전부 RestControllerAdvice 에서 처리되는가?
    // A. 여기서 던지는 BusinessException은 GlobalExceptionHandler의 RestControllerAdvice에서 일괄 처리된다.
    public Big3SelectionResponse getTodayBig3(String userId) {
        return big3SelectionRepository.findByUserIdAndSelectedDate(userId, LocalDate.now())
                .map(Big3Selection::toResponse)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "selectedDate", LocalDate.now().toString()
                        )
                ));
    }

    private List<String> deduplicate(List<String> itemIds) {
        return new ArrayList<>(new LinkedHashSet<>(itemIds));
    }

    private List<InboxItem> findInboxItemsInRequestOrder(String userId, List<String> itemIds) {
        Map<String, InboxItem> indexedItems = new LinkedHashMap<>();
        inboxItemRepository.findAllByUserIdAndIdIn(userId, itemIds)
                .forEach(item -> indexedItems.put(item.getId(), item));

        return itemIds.stream()
                .map(indexedItems::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
