// k6 Stress Test Script for DevBet API
// Run with: docker run --rm -i --network=host grafana/k6 run - < stress-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const getItemsTrend = new Trend('get_items_duration');
const buyItemTrend = new Trend('buy_item_duration');

// Test configuration
export const options = {
    stages: [
        { duration: '30s', target: 50 },  // Ramp up to 50 VUs
        { duration: '1m', target: 50 },   // Stay at 50 VUs
        { duration: '30s', target: 0 },   // Ramp down to 0
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],  // 95% of requests must complete below 500ms
        errors: ['rate<0.1'],              // Error rate must be less than 10%
    },
};

const BASE_URL = 'http://localhost:8081/api/v1';

// Test user ID (for X-User-Id header)
const TEST_USER_ID = '1';

const headers = {
    'Content-Type': 'application/json',
    'X-User-Id': TEST_USER_ID,
};

export default function () {
    // Scenario 1: Get Items (High frequency - 80% of requests)
    if (Math.random() < 0.8) {
        getItems();
    } else {
        // Scenario 2: Buy Item (Lower frequency - 20% of requests)
        buyItem();
    }

    sleep(1); // Think time between requests
}

function getItems() {
    const res = http.get(`${BASE_URL}/shop/items`, { headers });

    getItemsTrend.add(res.timings.duration);

    const checkResult = check(res, {
        'GET /shop/items: status is 200': (r) => r.status === 200,
        'GET /shop/items: response time < 500ms': (r) => r.timings.duration < 500,
        'GET /shop/items: has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.success !== undefined;
            } catch {
                return false;
            }
        },
    });

    errorRate.add(!checkResult);
}

function buyItem() {
    const payload = JSON.stringify({
        itemId: 1,      // Assuming item ID 1 exists
        quantity: 1,
    });

    const res = http.post(`${BASE_URL}/shop/buy`, payload, { headers });

    buyItemTrend.add(res.timings.duration);

    const checkResult = check(res, {
        'POST /shop/buy: status is 200 or 400': (r) => r.status === 200 || r.status === 400,
        'POST /shop/buy: response time < 500ms': (r) => r.timings.duration < 500,
    });

    errorRate.add(!checkResult);
}

// Optional: Setup function to verify server is running
export function setup() {
    const res = http.get(`${BASE_URL}/shop/items`);
    if (res.status !== 200) {
        console.error(`Server not ready. Status: ${res.status}`);
    } else {
        console.log('Server is ready. Starting stress test...');
    }
}

// Optional: Teardown function for cleanup
export function teardown(data) {
    console.log('Stress test completed.');
}
