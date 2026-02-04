package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Follow Entity - 사용자 간 팔로우 관계
 * 
 * Fan-out 아키텍처에서 팔로워 목록 조회에 사용됨
 */
@Entity
@Table(name = "follow", uniqueConstraints = {
        @UniqueConstraint(name = "uk_follow_follower_followee", columnNames = { "follower_id", "followee_id" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Follow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 팔로우하는 사용자 (나)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /**
     * 팔로우 당하는 사용자 (상대방)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private User followee;

    // ===== Factory Method =====

    public static Follow of(User follower, User followee) {
        return Follow.builder()
                .follower(follower)
                .followee(followee)
                .build();
    }
}
