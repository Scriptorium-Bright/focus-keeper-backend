package com.focuskeeper.reboot.recovery.planning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "execution_units")
/**
 * Big3 항목 아래에서 실제 timebox에 배정할 수 있는 실행 단위다.
 */
public class ExecutionUnit {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "big3_selection_item_id", nullable = false, length = 36)
    private String big3SelectionItemId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    protected ExecutionUnit() {
    }

    private ExecutionUnit(String id, String big3SelectionItemId, String title) {
        this.id = id;
        this.big3SelectionItemId = big3SelectionItemId;
        this.title = title;
    }

    public static ExecutionUnit create(String big3SelectionItemId, String title) {
        return new ExecutionUnit(UUID.randomUUID().toString(), big3SelectionItemId, title);
    }

    public String getId() {
        return id;
    }

    public String getBig3SelectionItemId() {
        return big3SelectionItemId;
    }

    public String getTitle() {
        return title;
    }
}
