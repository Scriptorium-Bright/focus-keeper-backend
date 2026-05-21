import { useEffect, useReducer, useState } from "react";
import {
  allocateTimeboxes,
  checkInFailure,
  completeRecoverySession,
  getAlerts,
  getBatchOverview,
  getRecoveryLoopOverview,
  interruptRecoverySession,
  restartRecovery,
  saveInboxItems,
  selectBig3,
  startRecoverySession
} from "./api";
import { ContextStage } from "./components/ContextStage";
import { ExecuteStage } from "./components/ExecuteStage";
import { InsightStage } from "./components/InsightStage";
import { PlanStage } from "./components/PlanStage";
import { StatusRail } from "./components/StatusRail";
import { Toast } from "./components/Toast";
import {
  createInitialWorkflowState,
  persistContext,
  workflowReducer
} from "./state";
import type { AllocateTimeboxPayload, FailureReason } from "./types";

type PendingAction =
  | "inbox"
  | "big3"
  | "timeboxes"
  | "session"
  | "failure"
  | "restart"
  | "insights"
  | "alerts";

const stagePaths = ["/context", "/plan", "/execute", "/insights"];

function stageFromPath(pathname: string) {
  const stage = stagePaths.indexOf(pathname);
  return stage >= 0 ? stage : 0;
}

export function App() {
  const [state, dispatch] = useReducer(workflowReducer, undefined, createInitialWorkflowState);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);

  useEffect(() => {
    dispatch({ type: "stageChanged", stage: stageFromPath(window.location.pathname) });

    const handlePopState = () => {
      dispatch({ type: "stageChanged", stage: stageFromPath(window.location.pathname) });
    };
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  useEffect(() => {
    const workspace = document.querySelector(".workspace");
    if (workspace instanceof HTMLElement && typeof workspace.scrollIntoView === "function") {
      workspace.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [state.activeStage]);

  useEffect(() => {
    if (!toastMessage) {
      return;
    }
    const timeoutId = window.setTimeout(() => setToastMessage(null), 2200);
    return () => window.clearTimeout(timeoutId);
  }, [toastMessage]);

  const showToast = (message: string) => {
    setToastMessage(message);
  };

  const ensureContext = () => {
    if (!state.userId.trim() || !state.metricDate) {
      showToast("userId와 metricDate를 먼저 입력해 주세요.");
      return false;
    }
    return true;
  };

  const runAction = async (action: PendingAction, task: () => Promise<void>) => {
    setPendingAction(action);
    try {
      await task();
      return true;
    } catch (error) {
      showToast(error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.");
      return false;
    } finally {
      setPendingAction(null);
    }
  };

  const setStage = (stage: number, replace = false) => {
    const path = stagePaths[stage] ?? stagePaths[0];
    if (window.location.pathname !== path) {
      if (replace) {
        window.history.replaceState({}, "", path);
      } else {
        window.history.pushState({}, "", path);
      }
    }
    dispatch({ type: "stageChanged", stage });
  };

  const handleContextSave = (userId: string, metricDate: string) => {
    const trimmedUserId = userId.trim();
    if (!trimmedUserId || !metricDate) {
      showToast("userId와 metricDate를 먼저 입력해 주세요.");
      return;
    }

    persistContext(trimmedUserId, metricDate);
    dispatch({ type: "contextSaved", userId: trimmedUserId, metricDate });
    setStage(1, true);
    showToast("검증 컨텍스트를 저장했습니다.");
  };

  const handleSaveInbox = async (contents: string[]) => {
    if (!ensureContext()) {
      return false;
    }
    if (contents.length === 0) {
      showToast("한 줄 이상 입력해 주세요.");
      return false;
    }

    return runAction("inbox", async () => {
      const response = await saveInboxItems(state.userId, contents);
      dispatch({ type: "inboxSaved", items: response.savedItems });
      showToast(`${response.savedCount}개의 Inbox Item을 저장했습니다.`);
    });
  };

  const handleSelectBig3 = async (itemIds: string[]) => {
    if (!ensureContext()) {
      return false;
    }
    if (itemIds.length === 0) {
      showToast("Big 3를 최소 1개 선택해 주세요.");
      return false;
    }

    return runAction("big3", async () => {
      const response = await selectBig3(state.userId, itemIds);
      dispatch({ type: "big3Selected", items: response.selectedItems });
      showToast(`${response.selectedCount}개의 Big 3를 확정했습니다.`);
    });
  };

  const handleAllocateTimeboxes = async (payload: AllocateTimeboxPayload[]) => {
    if (!ensureContext()) {
      return false;
    }
    if (payload.length === 0) {
      showToast("Big 3를 먼저 선택해 주세요.");
      return false;
    }

    const firstRecoveryCount = payload.filter((item) => item.firstRecoveryBlock).length;
    if (firstRecoveryCount !== 1) {
      showToast("첫 복귀 블록은 정확히 1개여야 합니다.");
      return false;
    }

    return runAction("timeboxes", async () => {
      const response = await allocateTimeboxes(state.userId, payload);
      dispatch({ type: "timeboxesAllocated", timeboxes: response.timeboxes });
      showToast(`${response.allocatedCount}개의 타임박스를 만들었습니다.`);
    });
  };

  const handleStartSession = async (timeboxId: string) => {
    if (!ensureContext()) {
      return;
    }

    await runAction("session", async () => {
      const session = await startRecoverySession(state.userId, timeboxId);
      dispatch({ type: "sessionChanged", session });
      showToast("복귀 세션을 시작했습니다.");
    });
  };

  const handleCompleteSession = async () => {
    if (!state.activeSession) {
      showToast("먼저 세션을 시작해 주세요.");
      return;
    }

    await runAction("session", async () => {
      const session = await completeRecoverySession(state.userId, state.activeSession!.sessionId);
      dispatch({ type: "sessionChanged", session });
      showToast("세션을 완료했습니다.");
    });
  };

  const handleInterruptSession = async () => {
    if (!state.activeSession) {
      showToast("먼저 세션을 시작해 주세요.");
      return;
    }

    await runAction("session", async () => {
      const session = await interruptRecoverySession(state.userId, state.activeSession!.sessionId);
      dispatch({ type: "sessionChanged", session });
      showToast("세션을 중단 상태로 바꿨습니다.");
    });
  };

  const handleCheckInFailure = async (reason: FailureReason, note: string) => {
    if (!state.activeSession) {
      showToast("실패 체크인은 활성 세션이 있어야 합니다.");
      return;
    }

    await runAction("failure", async () => {
      const failure = await checkInFailure(state.userId, state.activeSession!.sessionId, reason, note.trim());
      dispatch({ type: "failureCheckedIn", failure });
      showToast("실패 이벤트를 기록했습니다.");
    });
  };

  const handleRestart = async () => {
    if (!state.latestFailureEventId) {
      showToast("실패 체크인을 먼저 기록해 주세요.");
      return;
    }

    await runAction("restart", async () => {
      const response = await restartRecovery(state.userId, state.latestFailureEventId!);
      dispatch({ type: "sessionChanged", session: response.recoverySession });
      showToast("10분 재시작 세션을 만들었습니다.");
    });
  };

  const handleRefreshInsights = async () => {
    if (!ensureContext()) {
      return;
    }

    await runAction("insights", async () => {
      const [recoveryOverview, batchOverview, alerts] = await Promise.all([
        getRecoveryLoopOverview(state.userId, state.metricDate),
        getBatchOverview(state.userId, state.metricDate),
        getAlerts(state.userId, state.alertsActiveOnly)
      ]);
      dispatch({ type: "insightsLoaded", recoveryOverview, batchOverview, alerts });
      showToast("Recovery / Batch overview를 갱신했습니다.");
    });
  };

  const handleToggleAlerts = async () => {
    if (!ensureContext()) {
      return;
    }

    const activeOnly = !state.alertsActiveOnly;
    dispatch({ type: "alertsModeChanged", activeOnly });

    await runAction("alerts", async () => {
      const alerts = await getAlerts(state.userId, activeOnly);
      dispatch({ type: "alertsLoaded", alerts });
      showToast(activeOnly ? "활성 알림을 조회했습니다." : "전체 알림을 조회했습니다.");
    });
  };

  const handleStageChange = (stage: number) => {
    setStage(stage);
  };

  const activeStage = (() => {
    switch (state.activeStage) {
      case 0:
        return (
          <ContextStage
            active
            userId={state.userId}
            metricDate={state.metricDate}
            onSave={handleContextSave}
            onStageChange={handleStageChange}
          />
        );
      case 1:
        return (
          <PlanStage
            active
            metricDate={state.metricDate}
            inboxItems={state.inboxItems}
            big3Items={state.big3Items}
            timeboxes={state.timeboxes}
            pendingAction={pendingAction}
            onSaveInbox={handleSaveInbox}
            onSelectBig3={handleSelectBig3}
            onAllocateTimeboxes={handleAllocateTimeboxes}
            onStageChange={handleStageChange}
          />
        );
      case 2:
        return (
          <ExecuteStage
            active
            timeboxes={state.timeboxes}
            activeSession={state.activeSession}
            latestFailure={state.latestFailure}
            latestFailureEventId={state.latestFailureEventId}
            pendingAction={pendingAction}
            onStartSession={handleStartSession}
            onCompleteSession={handleCompleteSession}
            onInterruptSession={handleInterruptSession}
            onCheckInFailure={handleCheckInFailure}
            onRestart={handleRestart}
            onStageChange={handleStageChange}
          />
        );
      default:
        return (
          <InsightStage
            active
            recoveryOverview={state.recoveryOverview}
            batchOverview={state.batchOverview}
            alerts={state.alerts}
            alertsActiveOnly={state.alertsActiveOnly}
            pendingAction={pendingAction}
            onRefreshInsights={handleRefreshInsights}
            onToggleAlerts={handleToggleAlerts}
            onStageChange={handleStageChange}
          />
        );
    }
  })();

  return (
    <>
      <div className="page-shell">
        <header className="topbar compact reveal">
          <div className="brand-lockup">
            <div className="brand-mark">R</div>
            <div className="brand-copy">
              <p className="eyebrow">REBOOTFOCUS VALIDATION</p>
              <h1>실패 이후를 다시 붙잡는 복귀 앱</h1>
              <p className="hero-body">
                기록, 재시작, 리포트가 실제로 한 흐름으로 이어지는지 확인하기 위한 검증용 앱
                셸입니다. UI는 mock이지만 API는 실제 서버를 그대로 호출합니다.
              </p>
            </div>
          </div>
          <div className="topbar-panel">
            <div className="panel-tag">Real API Mock</div>
            <strong>
              입력 -&gt; 실행 -&gt; 인사이트를
              <br />한 번에 검증
            </strong>
            <div className="hero-pills">
              <span>실패 기록</span>
              <span>재시작</span>
              <span>Recovery24</span>
            </div>
          </div>
        </header>

        <div className="app-layout">
          <StatusRail state={state} onStageChange={handleStageChange} />
          <main className="workspace">{activeStage}</main>
        </div>
      </div>
      <Toast message={toastMessage} />
    </>
  );
}
