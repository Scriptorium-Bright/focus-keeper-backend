package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.constant.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.dto.TimeboxResponse;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import com.focuskeeper.reboot.recovery.planning.validation.TimeboxAllocationValidator;
import com.focuskeeper.reboot.recovery.planning.validation.TimeboxOverlapValidator;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
/**
 * Big3에 선택된 항목을 실제 일정 블록(timebox)으로 구체화하는 서비스다.
 *
 * 입력 검증, Big3 소속 여부 확인, 시간 충돌 방지, 엔티티 생성까지를 한 흐름으로 처리한다.
 */
@RequiredArgsConstructor
public class TimeboxService {

    private final ExecutionUnitRepository executionUnitRepository;
    private final TimeboxRepository timeboxRepository;
    private final TimeboxAllocationValidator timeboxAllocationValidator;
    private final TimeboxOverlapValidator timeboxOverlapValidator;
//    private final GuardRepository guardRepository;


    /**
     * 요청받은 명령 목록을 검증한 뒤 recovery timebox로 확정해 저장한다.
     */
    @Transactional
    // critical
    public List<TimeboxResponse> allocateTimeboxes(String userId, List<TimeboxCommand> commands) {

//        guardRepository.findByUserId(userId).orElseThrow();

        timeboxAllocationValidator.validateTypes(commands);
        timeboxAllocationValidator.validateFirstRecoveryBlock(commands);

        Map<String, ExecutionUnit> executionUnits = indexExecutionUnits(userId, commands);
        timeboxAllocationValidator.validateExecutionUnits(commands, executionUnits);


        List<Timebox> pendingTimeboxes = materializeTimeboxes(userId, commands, executionUnits);
        List<Timebox> existingTimeboxes = timeboxRepository.findAllByUserIdOrderByStartAtAsc(userId);

        timeboxOverlapValidator.validate(
                existingTimeboxes,
                pendingTimeboxes
        );

        return timeboxRepository.saveAll(pendingTimeboxes).stream()
                .map(Timebox::toResponse)
                .toList();
    }

    private List<Timebox> getExistingTimeboxes(String userId, List<Timebox> pendingTimeboxes) {
        OffsetDateTime minStart = pendingTimeboxes.stream()
                .map(Timebox::getStartAt)
                .min(OffsetDateTime::compareTo)
                .orElseThrow();

        OffsetDateTime maxEnd = pendingTimeboxes.stream()
                .map(Timebox::getEndAt)
                .max(OffsetDateTime::compareTo)
                .orElseThrow();

        return timeboxRepository.findOverlappingForUpdate(userId, minStart, maxEnd);
    }

    /**
     * 세션 시작 전에 특정 timebox가 존재하는지, 그리고 WORK 타입인지 검증한다.
     */
    // 이름 수정 필요함

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

    // high
    public void cancelledTimeBoxesByUser (List<String> timeboxIds, String userId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Timebox> allByIdInAndUserId = timeboxRepository.findAllByIdInAndUserId(timeboxIds, userId);

        for (Timebox timebox : allByIdInAndUserId) {
            timebox.cancelledByUser(now);
        }
    }

    /**
     * 요청에 포함된 execution unit을 사용자 소유 범위에서 조회해 id 기준 맵으로 재구성한다.
     */
    private Map<String, ExecutionUnit> indexExecutionUnits(String userId, List<TimeboxCommand> commands) {
        List<String> executionUnitIds = commands.stream()
                .map(TimeboxCommand::executionUnitId)
                .distinct()
                .toList();
        return executionUnitRepository.findAllByIdInAndBig3Item_UserId(executionUnitIds, userId)
                .stream()
                .collect(Collectors.toMap(ExecutionUnit::getId, Function.identity()));
    }

    /**
     * 요청 DTO를 실제 저장 가능한 Timebox 엔티티 목록으로 materialize한다.
     *
     * 이 단계에서 문자열 시각/타입을 파싱하고, execution unit 제목도 snapshot처럼 함께 복사한다.
     */
    private List<Timebox> materializeTimeboxes(
            String userId,
            List<TimeboxCommand> commands,
            Map<String, ExecutionUnit> executionUnits) {

        List<Timebox> pendingTimeboxes = new ArrayList<>();
        commands.forEach((TimeboxCommand command) -> {
            OffsetDateTime startAt = parseDateTime("startAt", command.startAt());
            OffsetDateTime endAt = parseDateTime("endAt", command.endAt());


            timeboxAllocationValidator.validateStartTime(startAt, endAt);

            ExecutionUnit executionUnit = executionUnits.get(command.executionUnitId());
            pendingTimeboxes.add(Timebox.create(
                    userId,
                    executionUnit,
                    parseType(command.type()),
                    startAt,
                    endAt,
                    command.firstRecoveryBlock(),
                    OffsetDateTime.now()
            ));
        });
        // Q. Request의 명칭은 사용자 입력이 아닌가? 좀 뭔가 애매하지 않나
        // A. 맞다. 여기서는 이미 request를 검증/파싱해서 엔티티 후보로 만든 상태라 "request"보다는 "candidate"나
        //    "pending"이 더 정확하다. 지금 이름은 "아직 저장 전"이라는 뜻은 전달하지만, 계층 의미상은 다소 흐리다.
        return pendingTimeboxes;
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
                    // "startAt": "ISO-8601 형식의 날짜시간이어야 합니다." :
                    //   - "2026-05-20T22:15:00" = LocalDateTime 형식
                    //  - "2026-05-20T22:15:00+09:00" = OffsetDateTime 형식
            );
        }
    }
}
