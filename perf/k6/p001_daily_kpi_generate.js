import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const METRIC_DATE = __ENV.METRIC_DATE || formatLocalDate(new Date());
const ITERATION_COUNT = Number(__ENV.ITERATIONS || 24);
const USER_COUNT = Number(__ENV.SEED_USERS || ITERATION_COUNT);
const TIMEBOXES_PER_USER = Number(__ENV.TIMEBOXES_PER_USER || 3);
const TIMEBOX_START_HOUR = Number(__ENV.TIMEBOX_START_HOUR || 0);
const TIMEBOX_SPACING_MINUTES = Number(__ENV.TIMEBOX_SPACING_MINUTES || 30);
const TIMEBOX_DURATION_MINUTES = Number(__ENV.TIMEBOX_DURATION_MINUTES || 25);
const FAILURE_EVERY = Number(__ENV.FAILURE_EVERY || 3);
const FAILURE_OFFSET = Number(__ENV.FAILURE_OFFSET || 1);
const GENERATE_SLEEP_SECONDS = Number(__ENV.GENERATE_SLEEP_SECONDS || 0.2);
const GENERATE_P95_THRESHOLD_MS = Number(__ENV.GENERATE_P95_THRESHOLD_MS || 1000);

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: ITERATION_COUNT,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:daily_kpi_generate}': [`p(95)<${GENERATE_P95_THRESHOLD_MS}`],
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

function isoAtMinuteOfDay(metricDate, minuteOfDay) {
  const hour = Math.floor(minuteOfDay / 60);
  const minute = minuteOfDay % 60;
  return isoAt(metricDate, hour, minute);
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
  const timeboxes = [];
  const firstMinuteOfDay = TIMEBOX_START_HOUR * 60;

  for (let index = 0; index < TIMEBOXES_PER_USER; index += 1) {
    const startMinuteOfDay = firstMinuteOfDay + (index * TIMEBOX_SPACING_MINUTES);
    timeboxes.push({
      itemId: itemIds[index % itemIds.length],
      startAt: isoAtMinuteOfDay(metricDate, startMinuteOfDay),
      endAt: isoAtMinuteOfDay(metricDate, startMinuteOfDay + TIMEBOX_DURATION_MINUTES),
      type: 'WORK',
      firstRecoveryBlock: index === 0,
    });
  }

  const response = postJson(
    `${baseUrl}/api/v1/recovery/timeboxes`,
    {
      userId,
      timeboxes,
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

function shouldFail(index) {
  return FAILURE_EVERY > 0 && index % FAILURE_EVERY === FAILURE_OFFSET;
}

function ensureSeedConfigFitsInDay() {
  if (TIMEBOXES_PER_USER < 1) {
    fail(`TIMEBOXES_PER_USER must be >= 1. received=${TIMEBOXES_PER_USER}`);
  }
  if (TIMEBOX_DURATION_MINUTES < 1) {
    fail(`TIMEBOX_DURATION_MINUTES must be >= 1. received=${TIMEBOX_DURATION_MINUTES}`);
  }
  if (TIMEBOX_SPACING_MINUTES < 1) {
    fail(`TIMEBOX_SPACING_MINUTES must be >= 1. received=${TIMEBOX_SPACING_MINUTES}`);
  }

  const firstMinuteOfDay = TIMEBOX_START_HOUR * 60;
  const lastStartMinute = firstMinuteOfDay + ((TIMEBOXES_PER_USER - 1) * TIMEBOX_SPACING_MINUTES);
  const lastEndMinute = lastStartMinute + TIMEBOX_DURATION_MINUTES;

  if (TIMEBOX_START_HOUR < 0 || TIMEBOX_START_HOUR > 23) {
    fail(`TIMEBOX_START_HOUR must be between 0 and 23. received=${TIMEBOX_START_HOUR}`);
  }
  if (lastEndMinute >= 24 * 60) {
    fail(
      `timebox schedule exceeds a single day. startHour=${TIMEBOX_START_HOUR} ` +
      `count=${TIMEBOXES_PER_USER} spacing=${TIMEBOX_SPACING_MINUTES} duration=${TIMEBOX_DURATION_MINUTES}`,
    );
  }
}

function seedUser(baseUrl, userId, metricDate) {
  const itemIds = saveInboxItems(baseUrl, userId);
  selectBig3(baseUrl, userId, itemIds);
  const timeboxIds = allocateTimeboxes(baseUrl, userId, itemIds, metricDate);

  timeboxIds.forEach((timeboxId, index) => {
    const sessionId = startSession(baseUrl, userId, timeboxId);

    if (shouldFail(index)) {
      const failureEventId = checkInFailure(baseUrl, userId, sessionId);
      const restartedSessionId = restartRecovery(baseUrl, userId, failureEventId);
      completeSession(baseUrl, userId, restartedSessionId);
      return;
    }

    completeSession(baseUrl, userId, sessionId);
  });

  return {
    userId,
    metricDate,
  };
}

export function setup() {
  ensureSeedConfigFitsInDay();
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
  sleep(GENERATE_SLEEP_SECONDS);
}
