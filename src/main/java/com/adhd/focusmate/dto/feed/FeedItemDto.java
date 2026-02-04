package com.adhd.focusmate.dto.feed;

import com.adhd.focusmate.domain.type.FeedType;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Feed Item DTO - Redis List에 저장될 피드 아이템
 * 
 * Fan-out on Write 패턴에서 팔로워 타임라인에 Push되는 데이터
 */
@Builder
public record FeedItemDto(
        /**
         * 피드 고유 ID (UUID)
         */
        String feedId,

        /**
         * 작성자 ID
         */
        Long writerId,

        /**
         * 작성자 닉네임
         */
        String writerName,

        /**
         * 작성자 프로필 이미지 URL
         */
        String writerProfileUrl,

        /**
         * 챌린지 ID
         */
        Long challengeId,

        /**
         * 챌린지 제목
         */
        String challengeTitle,

        /**
         * 배팅 포인트 (젤리)
         */
        Long betPoints,

        /**
         * 인증 이미지 URL (필수)
         */
        String verificationImageUrl,

        /**
         * 피드 타입
         */
        FeedType type,

        /**
         * 생성 시간
         */
        LocalDateTime createdAt

) implements Serializable {

    /**
     * 챌린지 성공 피드 생성
     */
    public static FeedItemDto successFeed(
            String feedId,
            Long writerId,
            String writerName,
            String writerProfileUrl,
            Long challengeId,
            String challengeTitle,
            Long betPoints,
            String verificationImageUrl) {
        return FeedItemDto.builder()
                .feedId(feedId)
                .writerId(writerId)
                .writerName(writerName)
                .writerProfileUrl(writerProfileUrl)
                .challengeId(challengeId)
                .challengeTitle(challengeTitle)
                .betPoints(betPoints)
                .verificationImageUrl(verificationImageUrl)
                .type(FeedType.CHALLENGE_SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 챌린지 실패 피드 생성
     */
    public static FeedItemDto failFeed(
            String feedId,
            Long writerId,
            String writerName,
            String writerProfileUrl,
            Long challengeId,
            String challengeTitle,
            Long betPoints,
            String verificationImageUrl) {
        return FeedItemDto.builder()
                .feedId(feedId)
                .writerId(writerId)
                .writerName(writerName)
                .writerProfileUrl(writerProfileUrl)
                .challengeId(challengeId)
                .challengeTitle(challengeTitle)
                .betPoints(betPoints)
                .verificationImageUrl(verificationImageUrl)
                .type(FeedType.CHALLENGE_FAIL)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
