package com.adhd.focusmate.global.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정
 * - Producer: StringSerializer(key) + JsonSerializer(value)
 * - Consumer: StringDeserializer(key) + JsonDeserializer(value)
 * - Error Handling: DLT(Dead Letter Topic) + ExponentialBackOff + 커스텀 로깅
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // ===== Producer Configuration =====

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 전송 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ===== Consumer Configuration =====

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Trust all packages for JSON deserialization
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    // ===== Error Handling Configuration =====

    /**
     * Error Handler with Dead Letter Topic (DLT) and Exponential Backoff
     * - Failed messages are sent to {original-topic}.DLT after retries
     * - Retry policy: Initial 1000ms, Multiplier 2.0, Max 10000ms
     */
    @Bean
    public CommonErrorHandler errorHandler() {
        // DLT Recoverer: 실패한 메시지를 {topic}.DLT로 전송
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate(),
                (record, ex) -> {
                    log.error("[Kafka-DLT] 메시지를 DLT로 전송합니다. Topic: {}, Key: {}, Error: {}",
                            record.topic() + ".DLT",
                            record.key(),
                            ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition());
                });

        // Exponential Backoff: 1000ms → 2000ms → 4000ms → 8000ms → 10000ms(max)
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L); // 초기 대기 시간
        backOff.setMultiplier(2.0); // 배수
        backOff.setMaxInterval(10000L); // 최대 대기 시간
        backOff.setMaxElapsedTime(30000L); // 최대 총 재시도 시간

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 커스텀 Retry Listener 추가
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                long nextBackoff = (long) (1000 * Math.pow(2, deliveryAttempt - 1));
                nextBackoff = Math.min(nextBackoff, 10000); // max 10초

                log.warn("[Kafka-Retry] 재시도 {}/N - Topic: {}, Key: {}, NextBackoff: {}ms, Error: {}",
                        deliveryAttempt,
                        record.topic(),
                        record.key(),
                        nextBackoff,
                        ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                log.info("[Kafka-Retry] 복구 완료 - Topic: {}, Key: {}", record.topic(), record.key());
            }

            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                log.error("[Kafka-Retry] 복구 실패, DLT로 이동 - Topic: {}, Key: {}, Error: {}",
                        record.topic(),
                        record.key(),
                        original.getMessage());
            }
        });

        return errorHandler;
    }
}
