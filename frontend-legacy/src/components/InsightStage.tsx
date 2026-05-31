import type { BatchOverview, OperationsAlert, RecoveryLoopOverview } from "../types";
import { boolText, pretty } from "../utils";
import { JsonPanel } from "./JsonPanel";

interface InsightStageProps {
  active: boolean;
  recoveryOverview: RecoveryLoopOverview | null;
  batchOverview: BatchOverview | null;
  alerts: OperationsAlert[] | null;
  alertsActiveOnly: boolean;
  pendingAction: string | null;
  onRefreshInsights: () => Promise<void>;
  onToggleAlerts: () => Promise<void>;
  onStageChange: (stage: number) => void;
}

export function InsightStage({
  active,
  recoveryOverview,
  batchOverview,
  alerts,
  alertsActiveOnly,
  pendingAction,
  onRefreshInsights,
  onToggleAlerts,
  onStageChange
}: InsightStageProps) {
  const dailyKpi = recoveryOverview?.dailyKpi;

  return (
    <section className={`stage-panel ${active ? "is-active" : ""}`}>
      <section className="insight-card reveal">
        <div className="section-head">
          <div>
            <p className="eyebrow">04 INSIGHT</p>
            <h2>오늘의 복귀 리포트</h2>
          </div>
          <p className="section-note">Recovery loop와 batch overview, active alert를 같은 화면에서 봅니다.</p>
        </div>
        <div className="insight-actions">
          <button
            className="primary-button"
            type="button"
            disabled={pendingAction === "insights"}
            onClick={() => void onRefreshInsights()}
          >
            인사이트 새로고침
          </button>
          <button
            className="ghost-button"
            type="button"
            disabled={pendingAction === "alerts"}
            onClick={() => void onToggleAlerts()}
          >
            {alertsActiveOnly ? "전체 알림 보기" : "활성 알림만 보기"}
          </button>
        </div>

        <div className="summary-grid">
          <SummaryTile label="Recovery24" value={boolText(dailyKpi?.recovery24)} />
          <SummaryTile label="TTR (min)" value={dailyKpi?.ttrMinutes ?? "-"} />
          <SummaryTile label="Cycle Completion" value={dailyKpi?.cycleCompletionRate ?? "-"} />
          <SummaryTile
            label="Last Processed Date"
            value={batchOverview?.lastProcessedDate?.lastProcessedDate ?? "-"}
          />
        </div>

        <div className="insight-grid">
          <JsonPanel title="Recovery Overview" value={recoveryOverview} />
          <JsonPanel title="Batch Overview" value={batchOverview} />
          <div className="json-panel full-span">
            <h3>Alert Lifecycle</h3>
            <AlertLifecycle alerts={alerts} />
            <pre>{alerts == null ? "아직 조회하지 않았습니다." : pretty(alerts)}</pre>
          </div>
        </div>
      </section>

      <div className="stage-footer">
        <button className="ghost-button" type="button" onClick={() => onStageChange(2)}>
          이전 단계
        </button>
        <button className="secondary-button" type="button" onClick={() => onStageChange(0)}>
          처음부터 다시 보기
        </button>
      </div>
    </section>
  );
}

function SummaryTile({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="summary-tile">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function AlertLifecycle({ alerts }: { alerts: OperationsAlert[] | null }) {
  if (alerts == null) {
    return <div className="result-list empty-state">아직 조회하지 않았습니다.</div>;
  }

  if (alerts.length === 0) {
    return <div className="result-list empty-state">표시할 alert가 없습니다.</div>;
  }

  return (
    <div className="result-list item-list">
      {alerts.map((alert) => (
        <div className="item-row" key={alert.alertKey}>
          <div>
            <div>{alert.summary}</div>
            <small className="meta-kicker">
              {alert.status} · {alert.severity} · occurrence {alert.occurrenceCount} · reopen{" "}
              {alert.reopenCount}
            </small>
            <small className="meta-kicker">
              firstSeen {alert.firstSeenAt || "-"} / resolved {alert.resolvedAt || "-"}
            </small>
          </div>
          <span className="chip">{alert.stage}</span>
        </div>
      ))}
    </div>
  );
}
