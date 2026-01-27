package com.adhd.focusmate.repository;

import com.adhd.focusmate.domain.model.Item;
import com.adhd.focusmate.domain.model.type.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByItemTypeAndActiveTrue(ItemType itemType);
}
