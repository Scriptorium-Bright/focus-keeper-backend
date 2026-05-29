import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // 500명의 동시 접속자(Virtual Users)
  vus: 100,
  // 30초 동안 지속적으로 시나리오 반복 실행
  duration: '30s',
  
  // p95, p99 지표를 요약(summary)에 명시적으로 출력하기 위한 설정
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const BASE_URL = 'http://127.0.0.1:8080/api/v1/recovery';

export default function () {
  // 각 유저마다 고유한 userId 부여
  const userId = `load-tester-${__VU}-${__ITER}`;

  const headers = { 'Content-Type': 'application/json' };

  // ----------------------------------------------------
  // 1. Inbox Items 생성 (Brain Dump)
  // ----------------------------------------------------
  const inboxPayload = JSON.stringify({
    userId: userId,
    items: [
      { content: "Load Test Task 1" },
      { content: "Load Test Task 2" },
      { content: "Load Test Task 3" },
      { content: "Load Test Task 4" },
      { content: "Load Test Task 5" },
      { content: "Load Test Task 6" },
      { content: "Load Test Task 7" },
      { content: "Load Test Task 8" },
      { content: "Load Test Task 9" },
      { content: "Load Test Task 10" },
      { content: "Load Test Task 11" },
      { content: "Load Test Task 12" },
      { content: "Load Test Task 13" },
      { content: "Load Test Task 14" },
      { content: "Load Test Task 15" },
      { content: "Load Test Task 16" },
      { content: "Load Test Task 17" },
      { content: "Load Test Task 18" },
      { content: "Load Test Task 19" },
      { content: "Load Test Task 20" }
    ]
  });

  let inboxRes = http.post(`${BASE_URL}/inbox-items`, inboxPayload, { headers });
  
  check(inboxRes, {
    'inbox creation status is 200': (r) => r.status === 200,
  });

  // 응답 파싱 및 ID 추출 (서버 에러가 났을 경우를 대비해 방어 로직 추가)
  let inboxItemIds = [];
  try {
    const inboxBody = inboxRes.json();
    if (inboxBody.data && inboxBody.data.items) {
      inboxItemIds = inboxBody.data.items.map(i => i.id);
    }
  } catch (e) {
    console.error("Inbox Response parsing failed: " + inboxRes.body);
  }

  // Inbox 저장이 실패했으면 다음 시나리오로 넘어가지 않음
  if (inboxItemIds.length === 0) return;

  // 짧은 대기 (실제 사용자 행동 모사)
  sleep(0.5);

  // ----------------------------------------------------
  // 2. Big3 Selection 생성 (오늘의 집중할 일 3개 선택)
  // ----------------------------------------------------
  const big3Payload = JSON.stringify({
    userId: userId,
    itemIds: inboxItemIds.slice(0, 3) // 위에서 저장한 3개를 통째로 넘김
  });

  let big3Res = http.post(`${BASE_URL}/big3`, big3Payload, { headers });

  check(big3Res, {
    'big3 selection status is 200': (r) => r.status === 200,
  });

  let big3SelectionItemIds = [];
  try {
    const big3Body = big3Res.json();
    if (big3Body.data && big3Body.data.selectedItems) {
      big3SelectionItemIds = big3Body.data.selectedItems.map(i => i.big3SelectionItemId);
    }
  } catch (e) {
    console.error("Big3 Response parsing failed: " + big3Res.body);
  }

  if (big3SelectionItemIds.length === 0) return;

  sleep(0.5);

  // ----------------------------------------------------
  // 3. Execution Unit 생성 (각 Big3를 쪼개기 - 다건 생성)
  // ----------------------------------------------------
  // 생성된 Big3 아이템 3개에 대해 각각 Execution Unit을 2개씩 생성
  for (const selectionItemId of big3SelectionItemIds) {
    const unitPayload = JSON.stringify({
      userId: userId,
      big3SelectionItemId: selectionItemId,
      title: ["단위 쪼개기 1", "단위 쪼개기 2"] // DTO의 리스트 필드가 title임
    });

    let unitRes = http.post(`${BASE_URL}/execution-units`, unitPayload, { headers });

    check(unitRes, {
      'execution unit creation status is 200': (r) => r.status === 200,
    });
  }

  sleep(1);
}
