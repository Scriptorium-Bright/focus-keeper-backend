package com.focuskeeper.reboot.recovery.planning.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "daily_big3_boards",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_big3_boards_user_date", columnNames = {"user_id", "selected_date"})
        }
)
@Getter
/**
 * 특정 사용자가 특정 날짜에 선택한 오늘의 Big3 헤더 엔티티다.
 */
public class DailyBig3Board extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "selected_date", nullable = false)
    private LocalDate selectedDate;

    @Column(name = "selected_at", nullable = false)
    private OffsetDateTime selectedAt;

    @OneToMany(mappedBy = "dailyBig3Board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DailyBig3Entry> entries = new ArrayList<>();

    protected DailyBig3Board() {

    }

    private DailyBig3Board(String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        this.userId = userId;
        this.selectedDate = selectedDate;
        this.selectedAt = selectedAt;
        initializeCreatedAt(selectedAt);
    }

    /**
     * 오늘의 Big3 선택 헤더를 새로 만든다.
     */
    public static DailyBig3Board create(String userId, LocalDate selectedDate, OffsetDateTime selectedAt) {
        return new DailyBig3Board(userId, selectedDate, selectedAt);
    }

}
