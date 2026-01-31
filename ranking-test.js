import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend } from 'k6/metrics';

/**
 * Redis 캐시 성능 비교 테스트
 * - Cache HIT (Redis)
 * - No Cache (DB Direct)
 */

const cacheHitDuration = new Trend('cache_hit_duration', true);
const noCacheDuration = new Trend('no_cache_duration', true);
const rankingDuration = new Trend('ranking_duration', true);

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '20s', target: 50 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<200'],
        cache_hit_duration: ['p(95)<100'],
        no_cache_duration: ['p(95)<300'],  // DB 직접 조회는 더 느림 예상
        ranking_duration: ['p(95)<100'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8081';

export function setup() {
    console.log('Warming up cache...');
    // 캐시 워밍업 - profile만 (profile-nocache는 항상 DB)
    http.get(`${BASE_URL}/api/v1/users/1/profile`);
    sleep(1);
    console.log('Cache warmed up!');
}

export default function () {
    group('Cache HIT (Redis)', function () {
        const res = http.get(`${BASE_URL}/api/v1/users/1/profile`);
        check(res, {
            'cache hit status 200': (r) => r.status === 200,
        });
        cacheHitDuration.add(res.timings.duration);
    });

    group('No Cache (DB Direct)', function () {
        const res = http.get(`${BASE_URL}/api/v1/users/1/profile-nocache`);
        check(res, {
            'no cache status 200': (r) => r.status === 200,
        });
        noCacheDuration.add(res.timings.duration);
    });

    group('Ranking Top10 (Redis ZSET)', function () {
        const res = http.get(`${BASE_URL}/api/v1/ranking/top10`);
        check(res, {
            'ranking status 200': (r) => r.status === 200,
        });
        rankingDuration.add(res.timings.duration);
    });

    sleep(0.1);
}

/**
 * 예상 결과:
 * - cache_hit_duration: p95 < 50ms (Redis에서 가져옴)
 * - no_cache_duration: p95 ~ 50-100ms (매번 DB 조회)
 * - ranking_duration: p95 < 30ms (Redis ZSET)
 */
