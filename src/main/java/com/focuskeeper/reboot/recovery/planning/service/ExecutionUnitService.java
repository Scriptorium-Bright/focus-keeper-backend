package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3SelectionItem;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse.toResponse;

@Service
@Transactional(readOnly = true)
/**
 * Big3 하위 실행 단위 생성과 수정을 담당한다.
 */
public class ExecutionUnitService {

    private final Big3SelectionItemRepository big3SelectionItemRepository;
    private final ExecutionUnitRepository executionUnitRepository;

    public ExecutionUnitService(
            Big3SelectionItemRepository big3SelectionItemRepository,
            ExecutionUnitRepository executionUnitRepository
    ) {
        this.big3SelectionItemRepository = big3SelectionItemRepository;
        this.executionUnitRepository = executionUnitRepository;
    }

    @Transactional
    public List<ExecutionUnitResponse> createUnit(String userId, String big3SelectionItemId, List<String> titles) {
        Big3SelectionItem big3SelectionItem = big3SelectionItemRepository
                .findByIdAndSelection_UserId(big3SelectionItemId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("big3SelectionItemId", big3SelectionItemId)
                ));

        int currentCount = big3SelectionItem.getUnits().size();
        int newCount = titles.size();

        if (currentCount + newCount > 3) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("titles", "ExecutionUnit 아이템은 총 3개까지만 생성할 수 있습니다. (현재 " + currentCount + "개 존재)")
            );
        }

        return insertExecutionUnit(titles, big3SelectionItem);
    }

    private List<ExecutionUnitResponse> insertExecutionUnit(List<String> titles, Big3SelectionItem big3SelectionItem) {
        List<ExecutionUnitResponse> executionUnitResponses = new ArrayList<>();

        for (String title : titles) {
            ExecutionUnit executionUnit = ExecutionUnit.create(big3SelectionItem, title, OffsetDateTime.now());
            big3SelectionItem.getUnits().add(executionUnit); // 자식 리스트에 수동으로 넣어줘야 영속성 컨텍스트 내에서 부모가 인지함
            big3SelectionItem.updateStatusFromUnits();
            ExecutionUnitResponse response = toResponse(executionUnit);

            executionUnitResponses.add(response);
        }

        return executionUnitResponses;
    }

    @Transactional
    public ExecutionUnitResponse updateUnit(String userId, String executionUnitId, String title) {
        ExecutionUnit executionUnit = requireUnit(userId, executionUnitId);
        executionUnit.rename(title);
        executionUnit.getBig3SelectionItem().updateStatusFromUnits();
        
        return toResponse(executionUnitRepository.save(executionUnit));
    }

    public List<ExecutionUnitResponse> getExecutionUnits(String userId, String big3SelectionItemId) {
        return executionUnitRepository.findAllByBig3SelectionItem_IdAndBig3SelectionItem_Selection_UserIdOrderByCreatedAtAsc(
                big3SelectionItemId, userId
        ).stream().map(ExecutionUnitResponse::toResponse).toList();
    }

    @Transactional
    public ExecutionUnitResponse completeUnit(String userId, String executionUnitId) {
        ExecutionUnit executionUnit = requireUnit(userId, executionUnitId);

        if (executionUnit.getStatus() == ExecutionUnitStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    Map.of(
                            "executionUnitId", executionUnitId,
                            "currentStatus", executionUnit.getStatus().name(),
                            "targetStatus", ExecutionUnitStatus.COMPLETED.name()
                    )
            );
        }

        executionUnit.complete(OffsetDateTime.now());

        Big3SelectionItem parent = executionUnit.getBig3SelectionItem();
        parent.updateStatusFromUnits();

        return toResponse(executionUnitRepository.save(executionUnit));
    }

    private ExecutionUnit requireUnit(String userId, String executionUnitId) {
        return executionUnitRepository
                .findByIdAndBig3SelectionItem_Selection_UserId(executionUnitId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("executionUnitId", executionUnitId)
                ));
    }


}
