package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.port.ActiveSessionTerminator;
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

import static com.focuskeeper.reboot.recovery.planning.constant.ExecutionUnitStatus.COMPLETED;
import static com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse.toResponse;
import static com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus.OPEN;

@Service
@Transactional(readOnly = true)
/**
 * Big3 하위 실행 단위 생성과 수정을 담당한다.
 */
public class ExecutionUnitService {

    private final Big3ItemRepository big3ItemRepository;
    private final ExecutionUnitRepository executionUnitRepository;
    private final ActiveSessionTerminator activeSessionTerminator;

    public ExecutionUnitService(
            Big3ItemRepository big3ItemRepository,
            ExecutionUnitRepository executionUnitRepository,
            ActiveSessionTerminator activeSessionTerminator
    ) {
        this.big3ItemRepository = big3ItemRepository;
        this.executionUnitRepository = executionUnitRepository;
        this.activeSessionTerminator = activeSessionTerminator;
    }

    @Transactional
    // high
    public List<MultipleExecutionUnitResponse> createUnit(String userId, String big3ItemId, List<String> titles) {
        Big3Item big3Item = requireBig3ItemForUnitCreation(userId, big3ItemId);

        validateItemAcceptsExecutionUnits(big3Item);
        validateUnitExceed(titles.size(), big3Item.getUnits().size());

        return bulkExecutionUnit(titles, big3Item);
    }



    @Transactional
    // high
    public ExecutionUnitResponse singleInsertUnit(String userId, String big3ItemId, String title) {

        Big3Item big3Item = requireBig3ItemForUnitCreation(userId, big3ItemId);
        validateItemAcceptsExecutionUnits(big3Item);
        validateUnitExceed(1, big3Item.getUnits().size());

        ExecutionUnit executionUnit = ExecutionUnit.create(big3Item, title, OffsetDateTime.now());

        executionUnitRepository.save(executionUnit);

        return toResponse(executionUnit);
    }

    private Big3Item requireBig3ItemForUnitCreation(String userId, String big3ItemId) {
        return big3ItemRepository
                .findByIdAndUserIdForUpdate(big3ItemId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("big3ItemId", big3ItemId)
                ));
    }

    // medium
    private List<MultipleExecutionUnitResponse> bulkExecutionUnit(List<String> titles, Big3Item big3Item) {
        List<MultipleExecutionUnitResponse> executionUnitResponses = new ArrayList<>();

        for (String title : titles) {
            ExecutionUnit executionUnit = ExecutionUnit.create(big3Item, title, OffsetDateTime.now());
            big3Item.addExecutionUnit(executionUnit);
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
        executionUnit.getBig3Item().refreshCompletionStatusFromUnits();
        
        return toResponse(executionUnitRepository.save(executionUnit));
    }

    public List<ExecutionUnitResponse> getExecutionUnits(String userId, String big3ItemId) {
        return executionUnitRepository.findAllByBig3Item_IdAndBig3Item_UserIdOrderByCreatedAtAsc(
                big3ItemId, userId
        ).stream().map(ExecutionUnitResponse::toResponse).toList();
    }

    @Transactional
    // high
    public ExecutionUnitResponse completeUnit(String userId, String executionUnitId) {
        ExecutionUnit executionUnit = requireUnit(userId, executionUnitId);
        OffsetDateTime now = OffsetDateTime.now();

        if (executionUnit.getStatus() == COMPLETED) {
            return toResponse(executionUnit);
        }

        for (Timebox t : executionUnit.getTimeboxes()) {
            activeSessionTerminator.completeIfActive(t.getId(), t.getUserId(), now);
        }
        executionUnit.complete(now);

        for (Timebox t : executionUnit.getTimeboxes()) {
            t.cancelledBySystem(OffsetDateTime.now());
        }

        Big3Item parent = executionUnit.getBig3Item();
        parent.refreshCompletionStatusFromUnits();

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

    private static void validateUnitExceed(int newCount, int currentCount) {
        if (currentCount + newCount > 5) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    Map.of("titles", "ExecutionUnit 아이템은 총 5개까지만 생성할 수 있습니다. (현재 " + currentCount + "개 존재)")
            );
        }
    }


}
