import type {
  AllocateTimeboxPayload,
  AllocateTimeboxesResponse,
  ApiResponse,
  BatchOverview,
  FailureCheckInResponse,
  FailureReason,
  OperationsAlert,
  RecoveryLoopOverview,
  RecoverySession,
  RestartRecoveryResponse,
  SaveInboxItemsResponse,
  SelectBig3Response
} from "./types";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, "") ?? "";

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...init.headers
    },
    ...init
  });

  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || payload.success === false) {
    throw new Error(
      payload.success === false
        ? payload.error?.message ?? payload.message ?? "요청 처리 중 오류가 발생했습니다."
        : "요청 처리 중 오류가 발생했습니다."
    );
  }

  return payload.data;
}

function post<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: "POST",
    body: JSON.stringify(body)
  });
}

export function saveInboxItems(userId: string, contents: string[]) {
  return post<SaveInboxItemsResponse>("/api/v1/recovery/inbox-items", {
    userId,
    items: contents.map((content) => ({ content }))
  });
}

export function selectBig3(userId: string, itemIds: string[]) {
  return post<SelectBig3Response>("/api/v1/recovery/big3", { userId, itemIds });
}

export function allocateTimeboxes(userId: string, timeboxes: AllocateTimeboxPayload[]) {
  return post<AllocateTimeboxesResponse>("/api/v1/recovery/timeboxes", {
    userId,
    timeboxes
  });
}

export function startRecoverySession(userId: string, timeboxId: string) {
  return post<RecoverySession>("/api/v1/recovery/sessions/start", { userId, timeboxId });
}

export function completeRecoverySession(userId: string, sessionId: string) {
  return post<RecoverySession>("/api/v1/recovery/sessions/complete", { userId, sessionId });
}

export function interruptRecoverySession(userId: string, sessionId: string) {
  return post<RecoverySession>("/api/v1/recovery/sessions/interrupt", { userId, sessionId });
}

export function checkInFailure(
  userId: string,
  sessionId: string,
  reason: FailureReason,
  note: string
) {
  return post<FailureCheckInResponse>("/api/v1/recovery/failures/check-in", {
    userId,
    sessionId,
    reason,
    note
  });
}

export function restartRecovery(userId: string, failureEventId: string) {
  return post<RestartRecoveryResponse>("/api/v1/recovery/restarts", {
    userId,
    failureEventId
  });
}

export function getRecoveryLoopOverview(userId: string, metricDate: string) {
  return request<RecoveryLoopOverview>(
    `/api/v1/ops/overview/recovery-loop?userId=${encodeURIComponent(userId)}&metricDate=${metricDate}`
  );
}

export function getBatchOverview(userId: string, metricDate: string) {
  return request<BatchOverview>(
    `/api/v1/ops/overview/batch?userId=${encodeURIComponent(userId)}&metricDate=${metricDate}`
  );
}

export function getAlerts(userId: string, activeOnly: boolean) {
  return request<OperationsAlert[]>(
    `/api/v1/ops/alerts?userId=${encodeURIComponent(userId)}&activeOnly=${activeOnly}`
  );
}
