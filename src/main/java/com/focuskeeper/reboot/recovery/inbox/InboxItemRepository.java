package com.focuskeeper.reboot.recovery.inbox;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxItemRepository extends JpaRepository<InboxItemEntity, String> {

    List<InboxItemEntity> findAllByUserIdAndIdIn(String userId, Collection<String> ids);
}
