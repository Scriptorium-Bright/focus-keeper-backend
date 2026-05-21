import { useEffect, useState } from "react";

interface ContextStageProps {
  active: boolean;
  userId: string;
  metricDate: string;
  onSave: (userId: string, metricDate: string) => void;
  onStageChange: (stage: number) => void;
}

export function ContextStage({
  active,
  userId,
  metricDate,
  onSave,
  onStageChange
}: ContextStageProps) {
  const [draftUserId, setDraftUserId] = useState(userId);
  const [draftMetricDate, setDraftMetricDate] = useState(metricDate);

  useEffect(() => {
    setDraftUserId(userId);
    setDraftMetricDate(metricDate);
  }, [userId, metricDate]);

  return (
    <section className={`stage-panel ${active ? "is-active" : ""}`}>
      <section className="stage-intro reveal">
        <p className="eyebrow">01 CONTEXT</p>
        <h2>누가, 어느 날짜 기준으로 복귀 루프를 검증할지 먼저 고정합니다.</h2>
        <p className="section-note">저장된 컨텍스트는 이후 모든 API 호출과 인사이트 조회에 공통으로 사용됩니다.</p>
      </section>

      <div className="context-layout">
        <section className="context-card reveal">
          <div className="section-head">
            <div>
              <p className="eyebrow">USER SETUP</p>
              <h2>검증 컨텍스트</h2>
            </div>
            <p className="section-note">브라우저에 저장되며 다음 방문에서도 유지됩니다.</p>
          </div>
          <form
            className="context-grid"
            onSubmit={(event) => {
              event.preventDefault();
              onSave(draftUserId, draftMetricDate);
            }}
          >
            <label>
              <span>userId</span>
              <input
                name="userId"
                type="text"
                placeholder="demo-user"
                value={draftUserId}
                onChange={(event) => setDraftUserId(event.target.value)}
                required
              />
            </label>
            <label>
              <span>metricDate</span>
              <input
                name="metricDate"
                type="date"
                value={draftMetricDate}
                onChange={(event) => setDraftMetricDate(event.target.value)}
                required
              />
            </label>
            <button className="primary-button" type="submit">
              컨텍스트 저장
            </button>
          </form>
        </section>

        <section className="proof-card reveal">
          <div className="section-head">
            <div>
              <p className="eyebrow">SCENARIO</p>
              <h2>오늘의 검증 흐름</h2>
            </div>
          </div>
          <div className="scenario-list">
            <ScenarioStep index={1} title="계획 입력">
              머릿속 작업을 Inbox로 적고, 오늘 다시 붙잡을 Big 3를 고릅니다.
            </ScenarioStep>
            <ScenarioStep index={2} title="실행 검증">
              타임박스를 시작하고 실패 체크인과 10분 재시작 흐름을 직접 눌러봅니다.
            </ScenarioStep>
            <ScenarioStep index={3} title="결과 확인">
              Recovery24, TTR, lastProcessedDate, active alert를 같은 화면에서 확인합니다.
            </ScenarioStep>
          </div>
        </section>
      </div>

      <div className="stage-footer">
        <div className="stage-hint">user와 날짜를 먼저 저장하면 나머지 단계가 그 컨텍스트를 그대로 사용합니다.</div>
        <button className="primary-button" type="button" onClick={() => onStageChange(1)}>
          계획 단계로 이동
        </button>
      </div>
    </section>
  );
}

function ScenarioStep({
  index,
  title,
  children
}: {
  index: number;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="scenario-step">
      <span className="scenario-index">{index}</span>
      <div>
        <strong>{title}</strong>
        <p>{children}</p>
      </div>
    </div>
  );
}
