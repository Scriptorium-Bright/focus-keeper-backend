package com.adhd.focusmate.controller;

import com.adhd.focusmate.dto.event.ChallengeSuccessEvent;
import com.adhd.focusmate.service.challenge.ChallengeEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Kafka 테스트용 컨트롤러
 * 이벤트 발행 및 DLQ 테스트를 위한 엔드포인트 제공
 */
@Slf4j
@RestController
@RequestMapping("/test/kafka")
@RequiredArgsConstructor
public class KafkaTestController {

    private final ChallengeEventProducer challengeEventProducer;

    /**
     * 챌린지 성공 이벤트 발행 테스트
     * 
     * POST /test/kafka/success
     * Body: { "userId": 1, "challengeId": 100, "title": "30분 집중", "rewardPoints":
     * 50 }
     * 
     * DLQ 테스트: title에 "error" 포함 시 Consumer에서 예외 발생 → DLT로 이동
     */
    @PostMapping("/success")
    public ResponseEntity<Map<String, Object>> publishSuccessEvent(
            @RequestBody ChallengeSuccessEvent event) {

        log.info("[KafkaTest] Publishing ChallengeSuccessEvent: {}", event);

        // 타임스탬프가 없으면 현재 시간으로 생성
        ChallengeSuccessEvent eventToSend = event.timestamp() == null
                ? ChallengeSuccessEvent.of(event.userId(), event.challengeId(), event.title(), event.rewardPoints())
                : event;

        challengeEventProducer.sendSuccessEvent(eventToSend);

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "topic", "challenge-success",
                "event", eventToSend));
    }

    /**
     * 간단한 테스트용 엔드포인트
     * GET /test/kafka/ping
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("Kafka Test Controller is ready!");
    }
}
