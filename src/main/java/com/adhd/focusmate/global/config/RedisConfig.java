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
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 설정
 * - Key: String 직렬화 (읽기 쉬운 키)
 * - Value: JSON 직렬화 (Jackson2)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    // ===== Cache TTL Constants =====
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(60);
    private static final Duration USER_PROFILE_TTL = Duration.ofMinutes(30);
    private static final Duration CHALLENGE_INFO_TTL = Duration.ofMinutes(10);
    private static final Duration SHOP_ITEMS_TTL = Duration.ofMinutes(60);

    /**
     * RedisTemplate 설정 (직접 Redis 접근용 - Ranking 등)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value는 String으로 (ZSET의 member는 String)
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache용 CacheManager
     * - JDK Serialization 사용 (안정적, 모든 Serializable 객체 지원)
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 기본 캐시 설정 - JDK Serialization 사용
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues();

        // 캐시명별 Custom TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("userProfile", defaultConfig.entryTtl(USER_PROFILE_TTL));
        cacheConfigurations.put("challengeInfo", defaultConfig.entryTtl(CHALLENGE_INFO_TTL));
        cacheConfigurations.put("shopItems", defaultConfig.entryTtl(SHOP_ITEMS_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
