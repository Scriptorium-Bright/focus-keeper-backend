package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
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
/**
 * Big3에 선택된 항목을 실제 일정 블록(timebox)으로 구체화하는 서비스다.
 *
 * 입력 검증, Big3 소속 여부 확인, 시간 충돌 방지, 엔티티 생성까지를 한 흐름으로 처리한다.
 */
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
     * 요청받은 명령 목록을 검증한 뒤 recovery timebox로 확정해 저장한다.
     */
    @Transactional
    public List<TimeboxResponse> allocateTimeboxes(String userId, List<TimeboxCommand> commands) {
        timeboxAllocationValidator.validateTypes(commands);
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

    /**
     * 세션 시작 전에 특정 timebox가 존재하는지, 그리고 WORK 타입인지 검증한다.
     */
    public void getTimebox(String userId, String timeboxId) {
        Timebox timebox = timeboxRepository.findByIdAndUserId(timeboxId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("timeboxId", timeboxId)
                ));
        if (timebox.getType() != TimeboxType.WORK) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    Map.of("timeboxId", "BREAK timebox로는 복귀 세션을 시작할 수 없습니다.")
            );
        }
    }

    /**
     * 오늘의 Big3 항목을 itemId 기준 맵으로 재구성한다.
     */
    private Map<String, InboxItemResponse> indexSelectedItems(Big3SelectionResponse selection) {
        Map<String, InboxItemResponse> indexedItems = new LinkedHashMap<>();
        for (InboxItemResponse item : selection.selectedItems()) {
            indexedItems.put(item.id(), item);
        }
        return indexedItems;
    }

    /**
     * 요청 DTO를 실제 저장 가능한 Timebox 엔티티 목록으로 materialize한다.
     *
     * 이 단계에서 문자열 시각/타입을 파싱하고, Big3 item의 현재 제목(content)도 snapshot처럼 함께 복사한다.
     */
    private List<Timebox> materializeTimeboxes(
            String userId,
            List<TimeboxCommand> commands,
            Map<String, InboxItemResponse> selectedItems) {
        List<Timebox> requestedTimeboxes = new ArrayList<>();
        commands.forEach((TimeboxCommand command) -> {
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
                    parseType(command.type()),
                    startAt,
                    endAt,
                    command.firstRecoveryBlock(),
                    OffsetDateTime.now()
            ));
        });
        return requestedTimeboxes;
    }

    /**
     * 외부 문자열을 TimeboxType enum으로 변환한다.
     */
    private TimeboxType parseType(String rawType) {
        try {
            return TimeboxType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("type", "지원하지 않는 timebox type입니다.")
            );
        }
    }

    /**
     * 외부 문자열을 OffsetDateTime으로 파싱하고 형식 오류를 도메인 예외로 바꾼다.
     */
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
