package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.InboxItemDto;
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

    public TimeboxService(Big3Service big3Service, TimeboxRepository timeboxRepository) {
        this.big3Service = big3Service;
        this.timeboxRepository = timeboxRepository;
    }

    @Transactional
    public List<TimeboxDto> allocateTimeboxes(String userId, List<TimeboxCommand> commands) {
        validateFirstRecoveryBlock(commands);

        Big3SelectionDto selection = big3Service.getTodayBig3OrThrow(userId);
        Map<String, InboxItemDto> selectedItems = indexSelectedItems(selection);
        validateSelectedItems(commands, selectedItems);

        List<TimeboxEntity> requestedTimeboxes = materializeTimeboxes(userId, commands, selectedItems);
        validateOverlaps(
                userId,
                requestedTimeboxes.stream().map(TimeboxEntity::toDto).toList()
        );

        return timeboxRepository.saveAll(requestedTimeboxes).stream()
                .map(TimeboxEntity::toDto)
                .toList();
    }

    public void getTimeboxOrThrow(String userId, String timeboxId) {
        timeboxRepository.findByIdAndUserId(timeboxId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("timeboxId", timeboxId)
                ));
    }

    private void validateFirstRecoveryBlock(List<TimeboxCommand> commands) {
        long firstRecoveryBlockCount = commands.stream()
                .filter(TimeboxCommand::firstRecoveryBlock)
                .count();
        if (firstRecoveryBlockCount != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("timeboxes", "첫 복귀 블록은 정확히 1개여야 합니다.")
            );
        }
    }

    private Map<String, InboxItemDto> indexSelectedItems(Big3SelectionDto selection) {
        Map<String, InboxItemDto> indexedItems = new LinkedHashMap<>();
        for (InboxItemDto item : selection.selectedItems()) {
            indexedItems.put(item.id(), item);
        }
        return indexedItems;
    }

    private void validateSelectedItems(List<TimeboxCommand> commands, Map<String, InboxItemDto> selectedItems) {
        List<String> invalidItemIds = commands.stream()
                .map(TimeboxCommand::itemId)
                .filter(itemId -> !selectedItems.containsKey(itemId))
                .distinct()
                .toList();

        if (!invalidItemIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of(
                            "invalidItemIds", invalidItemIds,
                            "itemIds", "오늘의 Big3에 포함된 항목만 timebox로 배정할 수 있습니다."
                    )
            );
        }
    }

    private List<TimeboxEntity> materializeTimeboxes(
            String userId,
            List<TimeboxCommand> commands,
            Map<String, InboxItemDto> selectedItems
    ) {
        List<TimeboxEntity> requestedTimeboxes = new ArrayList<>();
        for (TimeboxCommand command : commands) {
            OffsetDateTime startAt = parseDateTime("startAt", command.startAt());
            OffsetDateTime endAt = parseDateTime("endAt", command.endAt());
            if (!startAt.isBefore(endAt)) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        Map.of("timeboxes", "startAt은 endAt보다 빨라야 합니다.")
                );
            }

            InboxItemDto sourceItem = selectedItems.get(command.itemId());
            requestedTimeboxes.add(TimeboxEntity.create(
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

    private void validateOverlaps(String userId, List<TimeboxDto> requestedTimeboxes) {
        List<TimeboxDto> existingTimeboxes = timeboxRepository.findAllByUserIdOrderByStartAtAsc(userId).stream()
                .map(TimeboxEntity::toDto)
                .toList();

        for (int index = 0; index < requestedTimeboxes.size(); index++) {
            TimeboxDto current = requestedTimeboxes.get(index);

            for (int otherIndex = index + 1; otherIndex < requestedTimeboxes.size(); otherIndex++) {
                TimeboxDto other = requestedTimeboxes.get(otherIndex);
                if (overlaps(current, other.startAt(), other.endAt())) {
                    throw conflictException(current);
                }
            }

            for (TimeboxDto existing : existingTimeboxes) {
                if (overlaps(existing, current.startAt(), current.endAt())) {
                    throw conflictException(existing);
                }
            }
        }
    }

    private boolean overlaps(TimeboxDto timebox, OffsetDateTime otherStartAt, OffsetDateTime otherEndAt) {
        return timebox.startAt().isBefore(otherEndAt) && timebox.endAt().isAfter(otherStartAt);
    }

    private BusinessException conflictException(TimeboxDto conflictingTimebox) {
        return new BusinessException(
                ErrorCode.CONFLICT,
                Map.of(
                        "conflictingTimeboxId", conflictingTimebox.id(),
                        "startAt", conflictingTimebox.startAt().toString(),
                        "endAt", conflictingTimebox.endAt().toString()
                )
        );
    }

    public record TimeboxCommand(
            String itemId,
            String startAt,
            String endAt,
            boolean firstRecoveryBlock
    ) {
    }
}
