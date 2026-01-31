package com.adhd.focusmate.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 설정
 * - Key: String (읽기 쉬운 키)
 * - Value: JSON (디버깅 용이)
 * - JDK Serialization 사용 금지 (바이너리 포맷)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // ===== Cache TTL Constants =====
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(60); // 기본 60분
    private static final Duration USER_PROFILE_TTL = Duration.ofMinutes(30); // 유저 프로필: 30분
    private static final Duration CHALLENGE_INFO_TTL = Duration.ofMinutes(10); // 챌린지 정보: 10분
    private static final Duration SHOP_ITEMS_TTL = Duration.ofMinutes(60); // 상점 아이템: 60분

    /**
     * RedisTemplate 설정
     * - Key/HashKey: StringRedisSerializer
     * - Value/HashValue: GenericJackson2JsonRedisSerializer (JSON)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key Serializer: String (readable)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value Serializer: JSON (debuggable)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache용 CacheManager
     * - 기본 TTL: 60분
     * - Custom TTL: 캐시명별 개별 설정
     * - Null 캐싱 비활성화 (Cache Penetration 방지)
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues() // null 캐싱 방지 (Cache Penetration 방지)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper())));

        // 캐시명별 Custom TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // userProfile: 30분 (빈번한 조회, 드문 업데이트)
        cacheConfigurations.put("userProfile", defaultConfig.entryTtl(USER_PROFILE_TTL));

        // challengeInfo: 10분 (빈번한 조회, 간헐적 업데이트)
        cacheConfigurations.put("challengeInfo", defaultConfig.entryTtl(CHALLENGE_INFO_TTL));

        // shopItems: 60분 (자주 조회, 거의 변경 없음)
        cacheConfigurations.put("shopItems", defaultConfig.entryTtl(SHOP_ITEMS_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * ObjectMapper 설정
     * - Java 8 DateTime 지원
     * - 날짜를 ISO-8601 문자열로 출력
     */
    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
