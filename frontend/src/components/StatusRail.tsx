import type { WorkflowState } from "../state";

interface StatusRailProps {
  state: WorkflowState;
  onStageChange: (stage: number) => void;
}

const stages = [
  { index: 0, title: "설정", description: "컨텍스트와 검증 시나리오" },
  { index: 1, title: "계획", description: "Inbox, Big 3, 타임박스" },
  { index: 2, title: "실행", description: "세션, 실패, 재시작" },
  { index: 3, title: "인사이트", description: "복귀 리포트와 운영 상태" }
];

export function StatusRail({ state, onStageChange }: StatusRailProps) {
  return (
    <aside className="control-rail">
      <section className="rail-compact-head reveal">
        <p className="eyebrow">WORKFLOW</p>
        <h2>{stages[state.activeStage]?.title ?? "설정"}</h2>
        <p>{stages[state.activeStage]?.description ?? "컨텍스트와 검증 시나리오"}</p>
      </section>

      <nav className="stage-nav reveal" aria-label="검증 단계">
        {stages.map((stage) => (
          <button
            className={`stage-button ${state.activeStage === stage.index ? "is-active" : ""}`}
            type="button"
            key={stage.index}
            onClick={() => onStageChange(stage.index)}
          >
            <span className="stage-index">{String(stage.index + 1).padStart(2, "0")}</span>
            <span className="stage-copy">
              <strong>{stage.title}</strong>
              <small>{stage.description}</small>
            </span>
          </button>
        ))}
      </nav>

      <section className="status-section reveal">
        <div className="rail-head">
          <p className="eyebrow">LIVE STATUS</p>
          <h3>현재 검증 상태</h3>
        </div>
        <div className="status-strip">
          <div className="status-pill">
            <span>현재 user</span>
            <strong>{state.userId || "-"}</strong>
          </div>
          <div className="status-pill">
            <span>Inbox / Big 3</span>
            <strong>
              {state.inboxItems.length} / {state.big3Items.length}
            </strong>
          </div>
          <div className="status-pill">
            <span>타임박스</span>
            <strong>{state.timeboxes.length}</strong>
          </div>
          <div className="status-pill">
            <span>세션 상태</span>
            <strong>{state.activeSession?.status ?? "대기 중"}</strong>
          </div>
        </div>
      </section>

    </aside>
  );
}
