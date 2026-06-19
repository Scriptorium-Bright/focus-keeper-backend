import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const USER_COUNT = Number(__ENV.USER_COUNT || 33333);
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '30s';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 32);
const MAX_VUS = Number(__ENV.MAX_VUS || 128);
const INDEX_MODE = __ENV.INDEX_MODE || 'unknown';
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 1000);

export const options = {
  scenarios: {
    get_today_big3: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{name:get_today_big3}': [`p(95)<${P95_THRESHOLD_MS}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function benchmarkUserId() {
  const index = (exec.scenario.iterationInTest % USER_COUNT) + 1;
  return `btree-load-${String(index).padStart(8, '0')}`;
}

export default function () {
  const userId = benchmarkUserId();
  const response = http.get(
    `${BASE_URL}/api/v1/recovery/big3?userId=${encodeURIComponent(userId)}`,
    {
      tags: {
        name: 'get_today_big3',
        index_mode: INDEX_MODE,
      },
    },
  );

  check(response, {
    'status is 200': (res) => res.status === 200,
    'success is true': (res) => res.status === 200 && res.json('success') === true,
    'three active entries returned': (res) => {
      if (res.status !== 200) {
        return false;
      }
      const entries = res.json('data.dailyBig3Entries');
      return Array.isArray(entries) && entries.length === 3;
    },
  });
}
