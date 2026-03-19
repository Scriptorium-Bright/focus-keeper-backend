package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.planning.dto.Big3SelectionResponse;
import com.focuskeeper.reboot.recovery.planning.dto.TimeboxResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import com.focuskeeper.reboot.recovery.planning.validation.TimeboxAllocationValidator;
import com.focuskeeper.reboot.recovery.planning.validation.TimeboxOverlapValidator;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TimeboxService {

    private final Big3Service big3Service;
    private final TimeboxRepository timeboxRepository;
    private final TimeboxAllocationValidator timeboxAllocationValidator;
    private final TimeboxOverlapValidator timeboxOverlapValidator;

    public TimeboxService(
            Big3Service big3Service,
            TimeboxRepository timeboxRepository,
            TimeboxAllocationValidator timeboxAllocationValidator,
            TimeboxOverlapValidator timeboxOverlapValidator
    ) {
        this.big3Service = big3Service;
        this.timeboxRepository = timeboxRepository;
        this.timeboxAllocationValidator = timeboxAllocationValidator;
        this.timeboxOverlapValidator = timeboxOverlapValidator;
    }

    /**
     *
     * @param userId
     * @param commands
     * @return Big3중 하나를 언제부터 언제까지 다시 붙잡을지 정하는 것, 즉 시간을 정해두는 거라고 보면 됨
     */
    @Transactional
    public List<TimeboxResponse> allocateTimeboxes(String userId, List<TimeboxCommand> commands) {
        timeboxAllocationValidator.validateFirstRecoveryBlock(commands);

        Big3SelectionResponse selection = big3Service.getTodayBig3(userId);
        Map<String, InboxItemResponse> selectedItems = indexSelectedItems(selection);
        timeboxAllocationValidator.validateSelectedItems(commands, selectedItems);

        List<Timebox> requestedTimeboxes = materializeTimeboxes(userId, commands, selectedItems);
        timeboxOverlapValidator.validate(
                timeboxRepository.findAllByUserIdOrderByStartAtAsc(userId),
                requestedTimeboxes
        );

        return timeboxRepository.saveAll(requestedTimeboxes).stream()
                .map(Timebox::toResponse)
                .toList();
    }

    public void getTimebox(String userId, String timeboxId) {
        timeboxRepository.findByIdAndUserId(timeboxId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("timeboxId", timeboxId)
                ));
    }

    private Map<String, InboxItemResponse> indexSelectedItems(Big3SelectionResponse selection) {
        Map<String, InboxItemResponse> indexedItems = new LinkedHashMap<>();
        for (InboxItemResponse item : selection.selectedItems()) {
            indexedItems.put(item.id(), item);
        }
        return indexedItems;
    }

    /**
     *
     * @param userId
     * @param commands
     * @param selectedItems
     * @return TimeBox를 구체화하는 로직
     */

    private List<Timebox> materializeTimeboxes(
            String userId,
            List<TimeboxCommand> commands,
            Map<String, InboxItemResponse> selectedItems) {
        List<Timebox> requestedTimeboxes = new ArrayList<>();
        for (TimeboxCommand command : commands) {
            OffsetDateTime startAt = parseDateTime("startAt", command.startAt());
            OffsetDateTime endAt = parseDateTime("endAt", command.endAt());
            if (!startAt.isBefore(endAt)) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        Map.of("timeboxes", "startAt은 endAt보다 빨라야 합니다.")
                );
            }

            InboxItemResponse sourceItem = selectedItems.get(command.itemId());
            requestedTimeboxes.add(Timebox.create(
                    userId,
                    sourceItem.id(),
                    sourceItem.content(),
                    startAt,
                    endAt,
                    command.firstRecoveryBlock(),
                    OffsetDateTime.now()
            ));
        }
        return requestedTimeboxes;
    }

    // Q. 다시 말했듯 검증 로직이 비즈니스 로직까지 들어와야 하는가? 지금 TimeBoxService에서 담당하는 부분이 너무 많은거같은데, 이거에 대한 의견이 필요함
    // A. 날짜 문자열 파싱과 입력 형식 검증은 엄밀히 말하면 DTO/컨트롤러 쪽 책임에 더 가깝다.
    // A. 지금은 service command가 String을 들고 있어서 여기서 처리하지만, 나중에는 OffsetDateTime으로 올려보내면 책임을 줄일 수 있다.
    // RQ. 두 시간 구간이라는 것이 무엇을 의미하는지?
    // A. 예를 들면 [09:00, 09:25] 같은 하나의 timebox 범위와 [09:10, 09:30] 같은 다른 범위를 비교하는 걸 뜻한다.
    // A. 결국 "한 사용자의 두 timebox가 같은 시각대를 동시에 차지하는가"를 보는 시간 범위 비교라고 이해하면 된다.
    private OffsetDateTime parseDateTime(String fieldName, String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of(fieldName, "ISO-8601 형식의 날짜시간이어야 합니다.")
            );
        }
    }
}
