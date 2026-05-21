import { useState } from "react";
import type {
  AllocatedTimebox,
  FailureCheckInResponse,
  FailureReason,
  RecoverySession
} from "../types";

interface ExecuteStageProps {
  active: boolean;
  timeboxes: AllocatedTimebox[];
  activeSession: RecoverySession | null;
  latestFailure: FailureCheckInResponse | null;
  latestFailureEventId: string | null;
  pendingAction: string | null;
  onStartSession: (timeboxId: string) => Promise<void>;
  onCompleteSession: () => Promise<void>;
  onInterruptSession: () => Promise<void>;
  onCheckInFailure: (reason: FailureReason, note: string) => Promise<void>;
  onRestart: () => Promise<void>;
  onStageChange: (stage: number) => void;
}

const failureReasons: FailureReason[] = [
  "TOO_BIG",
  "INTERRUPTION",
  "LOW_ENERGY",
  "UNCLEAR_NEXT_ACTION",
  "CONTEXT_SWITCHED"
];

export function ExecuteStage({
  active,
  timeboxes,
  activeSession,
  latestFailure,
  latestFailureEventId,
  pendingAction,
  onStartSession,
  onCompleteSession,
  onInterruptSession,
  onCheckInFailure,
  onRestart,
  onStageChange
}: ExecuteStageProps) {
  const [reason, setReason] = useState<FailureReason>("TOO_BIG");
  const [note, setNote] = useState("");

  return (
    <section className={`stage-panel ${active ? "is-active" : ""}`}>
      <section className="stage-intro reveal">
        <p className="eyebrow">03 EXECUTE</p>
        <h2>실패를 숨기지 않고 바로 기록한 뒤, 가장 짧은 재시작 경로를 확인합니다.</h2>
        <p className="section-note">세션 제어와 실패 체크인을 분리해 실제 앱처럼 더 명확하게 보이도록 구성했습니다.</p>
      </section>

      <div className="execution-grid">
        <article className="flow-card reveal">
          <div className="section-head">
            <div>
              <p className="eyebrow">SESSION</p>
              <h2>세션 제어</h2>
            </div>
            <p className="section-note">타임박스를 시작하고 완료 또는 중단 상태로 바꿉니다.</p>
          </div>
          <div className="console-panel">
            <SessionState session={activeSession} />
            <TimeboxActions
              timeboxes={timeboxes}
              disabled={pendingAction === "session"}
              onStartSession={onStartSession}
            />
            <div className="action-row">
              <button
                className="secondary-button"
                type="button"
                disabled={pendingAction === "session"}
                onClick={() => void onCompleteSession()}
              >
                세션 완료
              </button>
              <button
                className="ghost-button"
                type="button"
                disabled={pendingAction === "session"}
                onClick={() => void onInterruptSession()}
              >
                세션 중단
              </button>
            </div>
          </div>
        </article>

        <article className="flow-card reveal">
          <div className="section-head">
            <div>
              <p className="eyebrow">FAILURE &amp; RESTART</p>
              <h2>실패 기록과 재시작</h2>
            </div>
            <p className="section-note">중단 이유를 남기고 바로 10분 재시작을 만들어볼 수 있습니다.</p>
          </div>
          <form
            className="stack-form compact-form"
            onSubmit={async (event) => {
              event.preventDefault();
              await onCheckInFailure(reason, note);
            }}
          >
            <label>
              <span>실패 이유</span>
              <select value={reason} onChange={(event) => setReason(event.target.value as FailureReason)}>
                {failureReasons.map((failureReason) => (
                  <option value={failureReason} key={failureReason}>
                    {failureReason}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>메모</span>
              <textarea
                rows={4}
                placeholder="왜 끊겼는지 간단히 남겨보세요."
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
            </label>
            <div className="action-row">
              <button className="secondary-button" type="submit" disabled={pendingAction === "failure"}>
                실패 체크인
              </button>
              <button
                className="primary-button"
                type="button"
                disabled={!latestFailureEventId || pendingAction === "restart"}
                onClick={() => void onRestart()}
              >
                10분 재시작
              </button>
            </div>
          </form>
          <FailureResult failure={latestFailure} />
        </article>
      </div>

      <div className="stage-footer">
        <button className="ghost-button" type="button" onClick={() => onStageChange(1)}>
          이전 단계
        </button>
        <button className="primary-button" type="button" onClick={() => onStageChange(3)}>
          인사이트 보기
        </button>
      </div>
    </section>
  );
}

function SessionState({ session }: { session: RecoverySession | null }) {
  if (!session) {
    return <div className="status-card empty-state">아직 시작된 세션이 없습니다.</div>;
  }

  return (
    <div className="status-card">
      <strong>{session.status}</strong>
      <div className="meta-kicker">sessionId: {session.sessionId}</div>
      <div className="meta-kicker">timeboxId: {session.timeboxId}</div>
    </div>
  );
}

function TimeboxActions({
  timeboxes,
  disabled,
  onStartSession
}: {
  timeboxes: AllocatedTimebox[];
  disabled: boolean;
  onStartSession: (timeboxId: string) => Promise<void>;
}) {
  if (timeboxes.length === 0) {
    return <div className="chip-list empty-state">타임박스 할당 후 시작 버튼이 보입니다.</div>;
  }

  return (
    <div className="chip-list">
      {timeboxes.map((timebox) => (
        <button
          className="ghost-button"
          type="button"
          key={timebox.timeboxId}
          disabled={disabled}
          onClick={() => void onStartSession(timebox.timeboxId)}
        >
          {timebox.content}
        </button>
      ))}
    </div>
  );
}

function FailureResult({ failure }: { failure: FailureCheckInResponse | null }) {
  if (!failure) {
    return <div className="result-list empty-state">실패 이벤트와 재시작 결과가 여기에 표시됩니다.</div>;
  }

  return (
    <div className="result-list alert-list">
      <div className="alert-row">
        <strong>{failure.reason}</strong>
        <div>{failure.note || "메모 없음"}</div>
        <small className="meta-kicker">failureEventId: {failure.failureEventId}</small>
      </div>
    </div>
  );
}
