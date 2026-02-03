package com.adhd.focusmate.service.challenge;

import com.adhd.focusmate.dto.event.ChallengeSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 챌린지 이벤트 Producer
 * 챌린지 관련 이벤트를 Kafka로 발행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeEventProducer {

    private static final String TOPIC_CHALLENGE_SUCCESS = "challenge-success";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 챌린지 성공 이벤트 발행
     * - Partition Key: userId → 동일 사용자의 이벤트 순서 보장
     * - whenComplete: 성공/실패 로깅
     *
     * @param event 챌린지 성공 이벤트
     */
    public void sendSuccessEvent(ChallengeSuccessEvent event) {
        String partitionKey = event.userId().toString();

        kafkaTemplate.send(TOPIC_CHALLENGE_SUCCESS, partitionKey, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("[Kafka] SUCCESS - Topic: {}, Key: {}, Offset: {}, Partition: {}",
                                TOPIC_CHALLENGE_SUCCESS,
                                partitionKey,
                                result.getRecordMetadata().offset(),
                                result.getRecordMetadata().partition());
                    } else {
                        log.error("[Kafka] FAILURE - Topic: {}, Key: {}, Error: {}",
                                TOPIC_CHALLENGE_SUCCESS,
                                partitionKey,
                                ex.getMessage(),
                                ex);
                    }
                });
    }
}
