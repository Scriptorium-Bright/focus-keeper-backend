package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.domain.common.BaseEntity;
import com.adhd.focusmate.domain.model.type.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "is_ai_generated")
    private Boolean isAiGenerated;

    @Column(name = "energy_level")
    private Integer energyLevel;

    @Column(name = "estimated_time")
    private Integer estimatedTime; // in minutes

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TaskStatus status;
}
