package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse.toResponse;

@Service
@Transactional(readOnly = true)
/**
 * Big3 하위 실행 단위 생성과 수정을 담당한다.
 */
public class ExecutionUnitService {

    private final Big3ItemRepository big3ItemRepository;
    private final ExecutionUnitRepository executionUnitRepository;
    private final TimeboxService timeboxService;

    public ExecutionUnitService(
            Big3ItemRepository big3ItemRepository,
            ExecutionUnitRepository executionUnitRepository, TimeboxService timeboxService
    ) {
        this.big3ItemRepository = big3ItemRepository;
        this.executionUnitRepository = executionUnitRepository;
        this.timeboxService = timeboxService;
    }

    @Transactional
    public List<ExecutionUnitResponse> createUnit(String userId, String big3ItemId, List<String> titles) {
        Big3Item big3Item = getBig3ItemId(userId, big3ItemId);

        unitExceedException(titles.size(), big3Item.getUnits().size());

        return bulkExecutionUnit(titles, big3Item);
    }



    public ExecutionUnitResponse singleInsertUnit(String userId, String big3ItemId, String title) {

        Big3Item big3Item = getBig3ItemId(userId, big3ItemId);
        unitExceedException(1, big3Item.getUnits().size());

        ExecutionUnit executionUnit = ExecutionUnit.create(big3Item, title, OffsetDateTime.now());

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

    private List<ExecutionUnitResponse> bulkExecutionUnit(List<String> titles, Big3Item big3Item) {
        List<ExecutionUnitResponse> executionUnitResponses = new ArrayList<>();

        for (String title : titles) {
            ExecutionUnit executionUnit = ExecutionUnit.create(big3Item, title, OffsetDateTime.now());
            big3Item.getUnits().add(executionUnit); // 자식 리스트에 수동으로 넣어줘야 영속성 컨텍스트 내에서 부모가 인지함
            big3Item.updateStatusFromUnits();
            ExecutionUnitResponse response = toResponse(executionUnit);

            executionUnitResponses.add(response);
        }

        return executionUnitResponses;
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

        if (executionUnit.getStatus() == ExecutionUnitStatus.COMPLETED) {
            return toResponse(executionUnit);
        }

        for (Timebox t : executionUnit.getTimeboxes()) {
            RecoverySession startedSession = timeboxService.getStartedSession(t.getId(), t.getUserId());
            startedSession.complete(OffsetDateTime.now());
        }

        executionUnit.complete(OffsetDateTime.now());


        for (Timebox t : executionUnit.getTimeboxes()) {
            t.cancelledTimebox(OffsetDateTime.now());
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
                    Map.of("titles", "ExecutionUnit 아이템은 총 3개까지만 생성할 수 있습니다. (현재 " + currentCount + "개 존재)")
            );
        }
    }


}
