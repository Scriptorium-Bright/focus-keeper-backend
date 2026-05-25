package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.Big3SelectionItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Big3 선택 항목을 기준으로 하위 실행 단위를 만들 때 사용하는 저장소다.
 */
public interface Big3SelectionItemRepository extends JpaRepository<Big3SelectionItem, String> {

    Optional<Big3SelectionItem> findByIdAndSelection_UserId(String id, String userId);
}
