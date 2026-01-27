package com.adhd.focusmate.repository;

import com.adhd.focusmate.domain.model.UserItem;
import com.adhd.focusmate.domain.model.type.ItemType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    /**
     * 사용자-아이템 조회 (비관적 락)
     * 아이템 소비 시 동시성 보장
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ui FROM UserItem ui JOIN FETCH ui.item " +
            "WHERE ui.user.id = :userId AND ui.item.itemType = :itemType")
    Optional<UserItem> findByUserIdAndItemTypeForUpdate(
            @Param("userId") Long userId,
            @Param("itemType") ItemType itemType);

    Optional<UserItem> findByUserIdAndItemId(Long userId, Long itemId);
}
