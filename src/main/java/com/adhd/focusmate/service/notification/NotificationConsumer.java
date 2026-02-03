package com.adhd.focusmate.service.notification;

import com.adhd.focusmate.dto.event.ChallengeSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * 알림 서비스 Consumer
 * 챌린지 성공 이벤트를 수신하여 알림 처리
 */
@Slf4j
@Service
public class NotificationConsumer {

    /**
     * 챌린지 성공 이벤트 수신
     * - DLQ 테스트: 제목에 "error" 포함 시 예외 발생
     *
     * @param event 챌린지 성공 이벤트
     */
    @KafkaListener(topics = "challenge-success", groupId = "notification-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleChallengeSuccess(ChallengeSuccessEvent event) {
        log.info("[Notification] Received event - UserId: {}, ChallengeId: {}, Title: {}, Points: {}",
                event.userId(),
                event.challengeId(),
                event.title(),
                event.rewardPoints());

        // DLQ 테스트용: "error" 포함 시 의도적 예외 발생
        if (event.title() != null && event.title().toLowerCase().contains("error")) {
            log.warn("[Notification] Intentional failure triggered for testing DLQ");
            throw new RuntimeException("Intentional Failure: title contains 'error'");
        }

        // 알림 발송 로직
        log.info("[Notification] Sending notification to User [{}] for Challenge [{}]",
                event.userId(),
                event.title());

        // TODO: Send FCM / Email / Slack notification
        // fcmService.sendPush(event.userId(), "챌린지 성공!", event.title());
        // emailService.send(event.userId(), "축하합니다!", event.title());
        // slackService.sendMessage("#achievements", event.toString());

        log.info("[Notification] Successfully processed event for User [{}]", event.userId());
    }
}
