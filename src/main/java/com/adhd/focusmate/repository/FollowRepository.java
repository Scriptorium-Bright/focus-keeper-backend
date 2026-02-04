package com.adhd.focusmate.repository;

import com.adhd.focusmate.domain.model.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Follow Repository
 * 
 * Fan-out 아키텍처에서 핵심적으로 사용되는 조회 메서드 포함
 */
public interface FollowRepository extends JpaRepository<Follow, Long> {

    /**
     * 특정 사용자의 팔로워 ID 목록 조회 (Fan-out 핵심 쿼리)
     * 
     * @param followeeId 팔로우 당하는 사용자 ID
     * @return 팔로워 ID 목록
     */
    @Query("SELECT f.follower.id FROM Follow f WHERE f.followee.id = :followeeId")
    List<Long> findFollowerIdsByFolloweeId(@Param("followeeId") Long followeeId);

    /**
     * 팔로우 관계 존재 여부 확인
     */
    Optional<Follow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    /**
     * 팔로우 관계 존재 여부 (boolean)
     */
    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    /**
     * 팔로우 관계 삭제
     */
    void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    /**
     * 특정 사용자의 팔로워 수 조회
     */
    long countByFolloweeId(Long followeeId);

    /**
     * 특정 사용자가 팔로우하는 수 조회
     */
    long countByFollowerId(Long followerId);
}
