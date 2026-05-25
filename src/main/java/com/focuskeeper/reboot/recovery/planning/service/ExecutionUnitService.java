package com.focuskeeper.reboot.recovery.planning.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.dto.ExecutionUnitResponse;
import com.focuskeeper.reboot.recovery.planning.entity.Big3SelectionItem;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public ExecutionUnitResponse createUnit(String userId, String big3SelectionItemId, String title) {
        Big3SelectionItem big3SelectionItem = big3SelectionItemRepository
                .findByIdAndSelection_UserId(big3SelectionItemId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("big3SelectionItemId", big3SelectionItemId)
                ));

        ExecutionUnit executionUnit = ExecutionUnit.create(big3SelectionItem, title, OffsetDateTime.now());
        return toResponse(executionUnitRepository.save(executionUnit));
    }

    @Transactional
    public ExecutionUnitResponse updateUnit(String userId, String executionUnitId, String title) {
        ExecutionUnit executionUnit = executionUnitRepository
                .findByIdAndBig3SelectionItem_Selection_UserId(executionUnitId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("executionUnitId", executionUnitId)
                ));

        executionUnit.rename(title);
        return toResponse(executionUnitRepository.save(executionUnit));
    }

    private ExecutionUnitResponse toResponse(ExecutionUnit executionUnit) {
        return new ExecutionUnitResponse(
                executionUnit.getId(),
                executionUnit.getBig3SelectionItemId(),
                executionUnit.getTitle(),
                executionUnit.getCreatedAt().toString()
        );
    }
}
