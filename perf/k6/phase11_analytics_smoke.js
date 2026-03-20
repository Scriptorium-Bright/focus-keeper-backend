import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const METRIC_DATE = __ENV.METRIC_DATE || '2026-03-21';

export const options = {
  vus: Number(__ENV.VUS || 2),
  iterations: Number(__ENV.ITERATIONS || 6),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

function jsonHeaders() {
  return {
    headers: {
      'Content-Type': 'application/json',
    },
  };
}

export default function () {
  const userId = `k6-phase11-${__VU}-${__ITER}`;

  const generatePayload = JSON.stringify({
    userId,
    metricDate: METRIC_DATE,
  });

  const generateResponse = http.post(
    `${BASE_URL}/api/v1/recovery/analytics/kpis/daily`,
    generatePayload,
    jsonHeaders(),
  );

  check(generateResponse, {
    'daily kpi generate status is 200': (response) => response.status === 200,
    'daily kpi generate success is true': (response) => response.status === 200 && response.json('success') === true,
  });

  const responses = http.batch([
    ['GET', `${BASE_URL}/api/v1/recovery/analytics/kpis/daily?userId=${encodeURIComponent(userId)}&metricDate=${METRIC_DATE}`],
    ['GET', `${BASE_URL}/api/v1/recovery/analytics/kpis/daily/watermark?userId=${encodeURIComponent(userId)}`],
    ['GET', `${BASE_URL}/api/v1/recovery/analytics/kpis/daily/quality?userId=${encodeURIComponent(userId)}&metricDate=${METRIC_DATE}`],
  ]);

  check(responses[0], {
    'daily kpi fetch status is 200': (response) => response.status === 200,
    'daily kpi fetch success is true': (response) => response.status === 200 && response.json('success') === true,
  });
  check(responses[1], {
    'watermark fetch status is 200': (response) => response.status === 200,
    'watermark fetch success is true': (response) => response.status === 200 && response.json('success') === true,
  });
  check(responses[2], {
    'quality fetch status is 200': (response) => response.status === 200,
    'quality fetch success is true': (response) => response.status === 200 && response.json('success') === true,
  });

  const backfillPayload = JSON.stringify({
    userId,
    startDate: METRIC_DATE,
    endDate: METRIC_DATE,
  });

  const backfillResponse = http.post(
    `${BASE_URL}/api/v1/recovery/analytics/kpis/daily/backfill`,
    backfillPayload,
    jsonHeaders(),
  );

  check(backfillResponse, {
    'daily kpi backfill status is 200': (response) => response.status === 200,
    'daily kpi backfill success is true': (response) => response.status === 200 && response.json('success') === true,
  });

  sleep(0.2);
}
