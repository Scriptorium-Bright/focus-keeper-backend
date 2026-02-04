// k6 Wallet Concurrency Test Script
// Two Scenarios:
// 1. Same User Concurrency: Multiple VUs deducting from same wallet (tests pessimistic locking)
// 2. Multi-User Load: Many users independently accessing their wallets (tests horizontal scalability)
//
// Run with: k6 run wallet-concurrency-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// =====================================================
// Custom Metrics
// =====================================================
const deductSuccess = new Counter('deduct_success');
const deductFailed = new Counter('deduct_failed');
const insufficientBalance = new Counter('insufficient_balance');
const deductDuration = new Trend('deduct_duration');
const errorRate = new Rate('errors');

// =====================================================
// Test Configuration
// =====================================================
export const options = {
    scenarios: {
        // Scenario 1: Same User Concurrency Test (Pessimistic Lock Verification)
        same_user_concurrency: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            env: { SCENARIO: 'same_user' },
            tags: { scenario: 'same_user_concurrency' },
        },
        // Scenario 2: Multi-User Load Test (Horizontal Scalability)
        multi_user_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 50 },
                { duration: '30s', target: 50 },
                { duration: '15s', target: 0 },
            ],
            startTime: '35s', // Start after same_user scenario
            env: { SCENARIO: 'multi_user' },
            tags: { scenario: 'multi_user_load' },
        },
    },
    thresholds: {
        'http_req_duration{scenario:same_user_concurrency}': ['p(95)<500'],
        'http_req_duration{scenario:multi_user_load}': ['p(95)<300'],
        'errors': ['rate<0.05'], // Allow up to 5% errors (expected for insufficient balance)
    },
};

// =====================================================
// Configuration
// =====================================================
const BASE_URL = 'http://localhost:8081/api/v1';

// User IDs configured in data.sql (id 1, 2, 3)
// For multi-user test, we'll use IDs 100-149 (need to be seeded)
const SINGLE_USER_ID = 1;  // High balance user (100,000)
const MULTI_USER_START_ID = 100;
const MULTI_USER_COUNT = 50;

const headers = {
    'Content-Type': 'application/json',
};

// =====================================================
// Setup: Verify server and check initial balances
// =====================================================
export function setup() {
    console.log('🚀 Starting Wallet Concurrency Test');

    // Check server health
    const healthCheck = http.get(`${BASE_URL}/wallet/1`);
    if (healthCheck.status !== 200) {
        console.error(`❌ Server not ready. Status: ${healthCheck.status}`);
        throw new Error('Server not available');
    }

    // Get initial balance for user 1
    const wallet = JSON.parse(healthCheck.body);
    console.log(`✅ Server ready. User 1 balance: ${wallet.data?.balance}`);

    return { initialBalance: wallet.data?.balance };
}

// =====================================================
// Main Test Function
// =====================================================
export default function () {
    const scenario = __ENV.SCENARIO;

    if (scenario === 'same_user') {
        sameUserConcurrencyTest();
    } else if (scenario === 'multi_user') {
        multiUserLoadTest();
    }
}

// =====================================================
// Scenario 1: Same User Concurrency Test
// Tests pessimistic lock by having 20 VUs concurrently deduct from same wallet
// =====================================================
function sameUserConcurrencyTest() {
    const userId = SINGLE_USER_ID;
    const deductAmount = 10; // Small amount to allow many ops

    // Deduct
    const deductPayload = JSON.stringify({
        userId: userId,
        amount: deductAmount,
        reason: 'PENALTY'
    });

    const startTime = Date.now();
    const res = http.post(`${BASE_URL}/wallet/deduct`, deductPayload, { headers });
    const duration = Date.now() - startTime;

    deductDuration.add(duration);

    const success = check(res, {
        'deduct: status is 200 or 400': (r) => r.status === 200 || r.status === 400,
        'deduct: response time < 500ms': (r) => r.timings.duration < 500,
    });

    if (res.status === 200) {
        deductSuccess.add(1);
        // Log occasionally for visibility
        if (Math.random() < 0.05) {
            const body = JSON.parse(res.body);
            console.log(`[Same User] Deduct OK - Balance: ${body.data?.balance}`);
        }
    } else if (res.status === 400) {
        insufficientBalance.add(1);
    } else {
        deductFailed.add(1);
        console.error(`[Same User] Unexpected status: ${res.status}`);
    }

    errorRate.add(!success);

    sleep(0.1); // 100ms think time
}

// =====================================================
// Scenario 2: Multi-User Load Test  
// Tests horizontal scalability with different users
// =====================================================
function multiUserLoadTest() {
    // Each VU gets a unique user ID based on VU number
    const vuId = __VU;
    const userId = MULTI_USER_START_ID + (vuId % MULTI_USER_COUNT);

    // Randomize between charge and deduct (70% read, 30% write)
    const action = Math.random();

    if (action < 0.5) {
        // 50%: Get balance
        const res = http.get(`${BASE_URL}/wallet/${userId}`, { headers });

        check(res, {
            'get balance: status is 200 or 404': (r) => r.status === 200 || r.status === 404,
            'get balance: response time < 300ms': (r) => r.timings.duration < 300,
        });

    } else if (action < 0.8) {
        // 30%: Charge (충전)
        const chargePayload = JSON.stringify({
            userId: userId,
            amount: 100,
            reason: 'CHARGE'
        });

        const res = http.post(`${BASE_URL}/wallet/charge`, chargePayload, { headers });

        const success = check(res, {
            'charge: status is 200': (r) => r.status === 200,
            'charge: response time < 300ms': (r) => r.timings.duration < 300,
        });

        if (success) {
            deductSuccess.add(1);
        }
        errorRate.add(!success);

    } else {
        // 20%: Deduct (차감)
        const deductPayload = JSON.stringify({
            userId: userId,
            amount: 50,
            reason: 'PENALTY'
        });

        const res = http.post(`${BASE_URL}/wallet/deduct`, deductPayload, { headers });

        const success = check(res, {
            'deduct: status is 200 or 400': (r) => r.status === 200 || r.status === 400,
            'deduct: response time < 300ms': (r) => r.timings.duration < 300,
        });

        if (res.status === 200) {
            deductSuccess.add(1);
        } else if (res.status === 400) {
            insufficientBalance.add(1);
        }
        errorRate.add(!success);
    }

    sleep(0.3);
}

// =====================================================
// Teardown: Report final state
// =====================================================
export function teardown(data) {
    console.log('\n📊 Test Complete!');
    console.log(`Initial User 1 Balance: ${data.initialBalance}`);

    // Check final balance
    const res = http.get(`${BASE_URL}/wallet/1`);
    if (res.status === 200) {
        const wallet = JSON.parse(res.body);
        const finalBalance = wallet.data?.balance;
        const consumed = data.initialBalance - finalBalance;
        console.log(`Final User 1 Balance: ${finalBalance}`);
        console.log(`Total Consumed: ${consumed}`);
    }

    console.log('\n🔍 Check Summary:');
    console.log('- Same User Concurrency: Tests pessimistic lock behavior');
    console.log('- Multi User Load: Tests horizontal scalability');
    console.log('- If no race conditions, balance should never go negative');
}
