package com.focuskeeper.reboot.recovery.inbox.repository;

import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxItemRepository extends JpaRepository<InboxItem, String> {

    List<InboxItem> findAllByUserIdAndIdIn(String userId, Collection<String> ids);
}
