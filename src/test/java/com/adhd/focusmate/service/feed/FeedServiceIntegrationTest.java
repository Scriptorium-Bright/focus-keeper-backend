package com.adhd.focusmate.service.feed;

import com.adhd.focusmate.domain.model.Follow;
import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.dto.event.ChallengeSuccessEvent;
import com.adhd.focusmate.dto.feed.FeedItemDto;
import com.adhd.focusmate.repository.FollowRepository;
import com.adhd.focusmate.repository.UserRepository;
import com.adhd.focusmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FeedServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private FeedService feedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Test
    @DisplayName("Fan-out on Write: 친구가 챌린지에 성공하면 내 피드에 격려(Cheer) 항목이 추가된다")
    void testFeedFanOut() {
        // Given
        User me = userRepository.save(User.builder().email("me@test.com").nickname("Me").build());
        User friend = userRepository.save(User.builder().email("friend@test.com").nickname("Friend").build());

        followRepository.save(Follow.of(me, friend)); // 내가 친구를 팔로우

        ChallengeSuccessEvent event = new ChallengeSuccessEvent(
                friend.getId(),
                100L,
                "Coding Sprint",
                500,
                java.time.Instant.now());

        // When
        kafkaTemplate.send("challenge-success", event);

        // Then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            List<FeedItemDto> myFeed = feedService.getMyFeed(me.getId(), 0, 10);

            assertThat(myFeed).isNotEmpty();
            FeedItemDto feedItem = myFeed.get(0);
            assertThat(feedItem.writerId()).isEqualTo(friend.getId());
            assertThat(feedItem.challengeTitle()).isEqualTo("Coding Sprint");
            // assertThat(feedItem.type()).isEqualTo(FeedType.CHALLENGE_SUCCESS); // TODO:
            // FeedType.SOS_SIGNAL 로직 추가 시 변경
        });
    }
}
