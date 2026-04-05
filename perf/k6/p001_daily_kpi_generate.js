import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const METRIC_DATE = __ENV.METRIC_DATE || formatLocalDate(new Date());
const ITERATION_COUNT = Number(__ENV.ITERATIONS || 24);
const USER_COUNT = Number(__ENV.SEED_USERS || ITERATION_COUNT);

export const options = {
  vus: Number(__ENV.VUS || 4),
  iterations: Number(__ENV.ITERATIONS || 24),
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:daily_kpi_generate}': ['p(95)<1000'],
    checks: ['rate>0.99'],
  },
};

function formatLocalDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function isoAt(metricDate, hour, minute) {
  const hh = String(hour).padStart(2, '0');
  const mm = String(minute).padStart(2, '0');
  return `${metricDate}T${hh}:${mm}:00+09:00`;
}

function jsonParams(name) {
  return {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: name ? { name } : undefined,
  };
}

function assertSuccess(response, message) {
  const passed = check(response, {
    [`${message} status is 200`]: (res) => res.status === 200,
    [`${message} success is true`]: (res) => res.status === 200 && res.json('success') === true,
  });
  if (!passed) {
    fail(`${message} failed: ${response.status} ${response.body}`);
  }
}

function postJson(url, payload, name) {
  return http.post(url, JSON.stringify(payload), jsonParams(name));
}

function saveInboxItems(baseUrl, userId) {
  const response = postJson(
    `${baseUrl}/api/v1/recovery/inbox-items`,
    {
      userId,
      items: [
        { content: 'P-001 raw reuse performance item 1' },
        { content: 'P-001 raw reuse performance item 2' },
        { content: 'P-001 raw reuse performance item 3' },
      ],
    },
    'seed_inbox',
  );
  assertSuccess(response, 'seed inbox items');
  return response.json('data.savedItems').map((item) => item.id);
}

function selectBig3(baseUrl, userId, itemIds) {
  const response = postJson(
    `${baseUrl}/api/v1/recovery/big3`,
    {
      userId,
      itemIds,
    },
    'seed_big3',
  );
  assertSuccess(response, 'seed big3');
}

function allocateTimeboxes(baseUrl, userId, itemIds, metricDate) {
  const response = postJson(
    `${baseUrl}/api/v1/recovery/timeboxes`,
    {
      userId,
      timeboxes: [
        {
          itemId: itemIds[0],
          startAt: isoAt(metricDate, 9, 0),
          endAt: isoAt(metricDate, 9, 25),
          type: 'WORK',
          firstRecoveryBlock: true,
        },
        {
          itemId: itemIds[1],
          startAt: isoAt(metricDate, 10, 0),
          endAt: isoAt(metricDate, 10, 25),
          type: 'WORK',
          firstRecoveryBlock: false,
        },
        {
          itemId: itemIds[2],
          startAt: isoAt(metricDate, 11, 0),
          endAt: isoAt(metricDate, 11, 30),
          type: 'WORK',
          firstRecoveryBlock: false,
        },
      ],
    },
    'seed_timeboxes',
  );
  assertSuccess(response, 'seed timeboxes');
  return response.json('data.timeboxes').map((timebox) => timebox.timeboxId);
}

function startSession(baseUrl, userId, timeboxId) {
  const response = postJson(
    `${baseUrl}/api/v1/recovery/sessions/start`,
    {
      userId,
      timeboxId,
    },
    'seed_start_session',
  );
  assertSuccess(response, 'start session');
  return response.json('data.sessionId');
}

function completeSession(baseUrl, userId, sessionId) {
  const response = postJson(
    `${baseUrl}/api/v1/recovery/sessions/complete`,
    {
      userId,
      sessionId,
    },
    'seed_complete_session',
  );
  assertSuccess(response, 'complete session');
}

function checkInFailure(baseUrl, userId, sessionId) {
  const response = postJson(
    `${baseUrl}/api/v1/recovery/failures/check-in`,
    {
      userId,
      sessionId,
      reason: 'INTERRUPTION',
      note: 'P-001 k6 seed interruption',
    },
    'seed_failure_check_in',
  );
  assertSuccess(response, 'failure check-in');
  return response.json('data.failureEventId');
}

function restartRecovery(baseUrl, userId, failureEventId) {
  const response = postJson(
    `${baseUrl}/api/v1/recovery/restarts`,
    {
      userId,
      failureEventId,
    },
    'seed_restart_recovery',
  );
  assertSuccess(response, 'restart recovery');
  return response.json('data.recoverySession.sessionId');
}

function seedUser(baseUrl, userId, metricDate) {
  const itemIds = saveInboxItems(baseUrl, userId);
  selectBig3(baseUrl, userId, itemIds);
  const timeboxIds = allocateTimeboxes(baseUrl, userId, itemIds, metricDate);

  const completedSessionId = startSession(baseUrl, userId, timeboxIds[0]);
  completeSession(baseUrl, userId, completedSessionId);

  const interruptedSessionId = startSession(baseUrl, userId, timeboxIds[1]);
  const failureEventId = checkInFailure(baseUrl, userId, interruptedSessionId);
  const restartedSessionId = restartRecovery(baseUrl, userId, failureEventId);
  completeSession(baseUrl, userId, restartedSessionId);

  const trailingSessionId = startSession(baseUrl, userId, timeboxIds[2]);
  completeSession(baseUrl, userId, trailingSessionId);

  return {
    userId,
    metricDate,
  };
}

export function setup() {
  const seededUsers = [];

  for (let index = 0; index < USER_COUNT; index += 1) {
    const userId = `p001-k6-user-${index}-${Date.now()}`;
    seededUsers.push(seedUser(BASE_URL, userId, METRIC_DATE));
  }

  return {
    seededUsers,
    metricDate: METRIC_DATE,
  };
}

export default function (data) {
  const seeded = data.seededUsers[exec.scenario.iterationInTest % data.seededUsers.length];
  const response = postJson(
    `${BASE_URL}/api/v1/recovery/analytics/kpis/daily`,
    {
      userId: seeded.userId,
      metricDate: seeded.metricDate,
    },
    'daily_kpi_generate',
  );

  assertSuccess(response, 'daily kpi generate');
  sleep(0.2);
}
