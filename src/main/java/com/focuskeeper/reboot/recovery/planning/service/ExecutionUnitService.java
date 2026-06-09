package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse;
import com.focuskeeper.reboot.recovery.planning.dto.MultipleExecutionUnitResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus.COMPLETED;
import static com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus.PLANNED;
import static com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse.toResponse;
import static com.focuskeeper.reboot.recovery.planning.Big3ItemStatus.OPEN;

@Service
@Transactional(readOnly = true)
/**
 * Big3 하위 실행 단위 생성과 수정을 담당한다.
 */
public class ExecutionUnitService {

    private final Big3ItemRepository big3ItemRepository;
    private final ExecutionUnitRepository executionUnitRepository;
    private final RecoverySessionRepository recoverySessionRepository;

    public ExecutionUnitService(
            Big3ItemRepository big3ItemRepository,
            ExecutionUnitRepository executionUnitRepository,
            RecoverySessionRepository recoverySessionRepository
    ) {
        this.big3ItemRepository = big3ItemRepository;
        this.executionUnitRepository = executionUnitRepository;
        this.recoverySessionRepository = recoverySessionRepository;
    }

    @Transactional
    public List<MultipleExecutionUnitResponse> createUnit(String userId, String big3ItemId, List<String> titles) {
        Big3Item big3Item = getBig3ItemId(userId, big3ItemId);

        validateItemAcceptsExecutionUnits(big3Item);
        unitExceedException(titles.size(), big3Item.getUnits().size());

        return bulkExecutionUnit(titles, big3Item);
    }



    @Transactional
    public ExecutionUnitResponse singleInsertUnit(String userId, String big3ItemId, String title) {

        Big3Item big3Item = getBig3ItemId(userId, big3ItemId);
        validateItemAcceptsExecutionUnits(big3Item);
        unitExceedException(1, big3Item.getUnits().size());

        ExecutionUnit executionUnit = ExecutionUnit.create(big3Item, title, OffsetDateTime.now());

        executionUnitRepository.save(executionUnit);

        return toResponse(executionUnit);
    }

    private Big3Item getBig3ItemId(String userId, String big3ItemId) {
        return big3ItemRepository
                .findByIdAndUserId(big3ItemId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("big3ItemId", big3ItemId)
                ));
    }

    private List<MultipleExecutionUnitResponse> bulkExecutionUnit(List<String> titles, Big3Item big3Item) {
        List<MultipleExecutionUnitResponse> executionUnitResponses = new ArrayList<>();

        for (String title : titles) {
            ExecutionUnit executionUnit = ExecutionUnit.create(big3Item, title, OffsetDateTime.now());
            big3Item.getUnits().add(executionUnit); // 자식 리스트에 수동으로 넣어줘야 영속성 컨텍스트 내에서 부모가 인지함
            big3Item.updateStatusFromUnits();
            MultipleExecutionUnitResponse response = MultipleExecutionUnitResponse.toResponse(executionUnit);

            executionUnitResponses.add(response);
        }

        return executionUnitResponses;
    }

    private void validateItemAcceptsExecutionUnits(Big3Item big3Item) {
        if (big3Item.getStatus() != OPEN) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    Map.of(
                            "big3ItemId", big3Item.getId(),
                            "status", big3Item.getStatus().name()
                    )
            );
        }
    }

    @Transactional
    public ExecutionUnitResponse updateUnit(String userId, String executionUnitId, String title) {
        ExecutionUnit executionUnit = requireUnit(userId, executionUnitId);
        executionUnit.rename(title);
        executionUnit.getBig3Item().updateStatusFromUnits();
        
        return toResponse(executionUnitRepository.save(executionUnit));
    }

    public List<ExecutionUnitResponse> getExecutionUnits(String userId, String big3ItemId) {
        return executionUnitRepository.findAllByBig3Item_IdAndBig3Item_UserIdOrderByCreatedAtAsc(
                big3ItemId, userId
        ).stream().map(ExecutionUnitResponse::toResponse).toList();
    }

    @Transactional
    public ExecutionUnitResponse completeUnit(String userId, String executionUnitId) {
        ExecutionUnit executionUnit = requireUnit(userId, executionUnitId);
        OffsetDateTime now = OffsetDateTime.now();

        if (executionUnit.getStatus() == COMPLETED) {
            return toResponse(executionUnit);
        }

        for (Timebox t : executionUnit.getTimeboxes()) {
            recoverySessionRepository.findByTimeboxIdAndUserIdAndStatus(
                    t.getId(),
                    t.getUserId(),
                    RecoverySessionStatus.STARTED
            ).ifPresent(session -> session.complete(now));
        }
         executionUnit.complete(now);
        // 조건부 쿼리 vs Version Optimisitic Lock
/*
        int updatedCount = executionUnitRepository.updateExeucutionUnitStatus(COMPLETED, now, PLANNED, executionUnitId);

        if(updatedCount == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Update Failure");
        }
*/

        for (Timebox t : executionUnit.getTimeboxes()) {
            t.cancelledBySystem(OffsetDateTime.now());
        }

        Big3Item parent = executionUnit.getBig3Item();
        parent.updateStatusFromUnits();

        ExecutionUnit save = executionUnitRepository.save(executionUnit);

        return toResponse(save);
    }

    private ExecutionUnit requireUnit(String userId, String executionUnitId) {
        return executionUnitRepository
                .findByIdAndBig3Item_UserId(executionUnitId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("executionUnitId", executionUnitId)
                ));
    }

    // 추후 exception으로 이관예정
    private static void unitExceedException(int newCount, int currentCount) {
        if (currentCount + newCount > 5) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("titles", "ExecutionUnit 아이템은 총 5개까지만 생성할 수 있습니다. (현재 " + currentCount + "개 존재)")
            );
        }
    }


}
