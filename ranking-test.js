import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend } from 'k6/metrics';

/**
 * Redis 캐시 성능 테스트
 * - User Profile API (@Cacheable)
 * - Ranking API (Redis ZSET)
 */

const userProfileDuration = new Trend('user_profile_duration', true);
const rankingDuration = new Trend('ranking_duration', true);

export const options = {
    stages: [
        { duration: '10s', target: 50 },  // Ramp-up
        { duration: '20s', target: 50 },  // Sustained
        { duration: '10s', target: 0 },   // Ramp-down
    ],
    thresholds: {
        http_req_duration: ['p(95)<200'],
        user_profile_duration: ['p(95)<100'],
        ranking_duration: ['p(95)<100'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8081';

export function setup() {
    // 캐시 워밍업
    console.log('Warming up cache...');
    http.get(`${BASE_URL}/api/v1/users/1/profile`);
    http.get(`${BASE_URL}/api/v1/ranking/top10`);
    sleep(1);
    console.log('Cache warmed up!');
}

export default function () {
    group('User Profile (Cache HIT)', function () {
        const res = http.get(`${BASE_URL}/api/v1/users/1/profile`);
        check(res, {
            'profile status 200': (r) => r.status === 200,
            'profile has data': (r) => JSON.parse(r.body).success === true,
        });
        userProfileDuration.add(res.timings.duration);
    });

    group('Ranking Top10 (Redis ZSET)', function () {
        const res = http.get(`${BASE_URL}/api/v1/ranking/top10`);
        check(res, {
            'ranking status 200': (r) => r.status === 200,
            'ranking has data': (r) => JSON.parse(r.body).success === true,
        });
        rankingDuration.add(res.timings.duration);
    });

    sleep(0.1);
}

/**
 * 예상 결과:
 * - user_profile_duration: p95 < 50ms (JDK Serialization 캐시)
 * - ranking_duration: p95 < 30ms (Redis ZSET)
 */
