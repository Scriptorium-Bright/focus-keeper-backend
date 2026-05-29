import React, { useEffect, useReducer, useState } from 'react';
import { View, Text, StyleSheet, SafeAreaView, Platform, StatusBar } from 'react-native';
import {
  allocateTimeboxes,
  checkInFailure,
  completeExecutionUnit,
  completeRecoverySession,
  createExecutionUnit,
  getAlerts,
  getBatchOverview,
  getRecoveryLoopOverview,
  interruptRecoverySession,
  restartRecovery,
  saveInboxItems,
  selectBig3,
  startRecoverySession
} from './src/api';
import { ContextStage } from './src/components/ContextStage';
import { ExecuteStage } from './src/components/ExecuteStage';
import { InsightStage } from './src/components/InsightStage';
import { PlanStage } from './src/components/PlanStage';
import { StatusRail } from './src/components/StatusRail';
import { Toast } from './src/components/Toast';
import {
  createInitialWorkflowState,
  persistContext,
  readSavedContext,
  workflowReducer
} from './src/state';
import type { AllocateTimeboxPayload, CreateExecutionUnitPayload, FailureReason } from './src/types';

type PendingAction = "inbox" | "big3" | "executionUnits" | "executionUnitCompletion" | "timeboxes" | "session" | "failure" | "restart" | "insights" | "alerts";

export default function App() {
  const [state, dispatch] = useReducer(workflowReducer, createInitialWorkflowState());
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);

  useEffect(() => {
    // Load context from AsyncStorage
    readSavedContext().then((saved) => {
      dispatch({ type: "contextLoaded", userId: saved.userId, metricDate: saved.metricDate });
    });
  }, []);

  useEffect(() => {
    if (!toastMessage) return;
    const timeoutId = setTimeout(() => setToastMessage(null), 2200);
    return () => clearTimeout(timeoutId);
  }, [toastMessage]);

  const showToast = (message: string) => setToastMessage(message);

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

  const handleStageChange = (stage: number) => {
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
    showToast("검증 컨텍스트를 저장했습니다.");
  };

  const handleSaveInbox = async (contents: string[]) => {
    if (!ensureContext()) return false;
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
    if (!ensureContext()) return false;
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

  const handleCreateExecutionUnits = async (payload: CreateExecutionUnitPayload[]) => {
    if (!ensureContext()) return false;
    if (payload.length === 0) {
      showToast("Big 3를 먼저 선택해 주세요.");
      return false;
    }
    const normalized = payload.map(u => ({ big3SelectionItemId: u.big3SelectionItemId, title: u.title.trim() }));
    if (normalized.some(u => !u.big3SelectionItemId || !u.title)) {
      showToast("실행 단위 제목을 모두 입력해 주세요.");
      return false;
    }
    return runAction("executionUnits", async () => {
      const units = await Promise.all(normalized.map(u => createExecutionUnit(state.userId, u)));
      dispatch({ type: "executionUnitsCreated", units });
      showToast(`${units.length}개의 실행 단위를 만들었습니다.`);
    });
  };

  const handleAllocateTimeboxes = async (payload: AllocateTimeboxPayload[]) => {
    if (!ensureContext()) return false;
    if (payload.length === 0) {
      showToast("Big 3를 먼저 선택해 주세요.");
      return false;
    }
    if (payload.filter(i => i.firstRecoveryBlock).length !== 1) {
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
    if (!ensureContext()) return;
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

  const handleCompleteExecutionUnit = async (executionUnitId: string) => {
    if (!ensureContext()) return;
    await runAction("executionUnitCompletion", async () => {
      const unit = await completeExecutionUnit(state.userId, executionUnitId);
      dispatch({ type: "executionUnitChanged", unit });
      showToast("실행 단위를 완료했습니다.");
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
    if (!ensureContext()) return;
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
    if (!ensureContext()) return;
    const activeOnly = !state.alertsActiveOnly;
    dispatch({ type: "alertsModeChanged", activeOnly });
    await runAction("alerts", async () => {
      const alerts = await getAlerts(state.userId, activeOnly);
      dispatch({ type: "alertsLoaded", alerts });
      showToast(activeOnly ? "활성 알림을 조회했습니다." : "전체 알림을 조회했습니다.");
    });
  };

  if (!state.isLoaded) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.loadingContainer}>
          <Text>로딩 중...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.header}>
        <Text style={styles.brandMark}>R</Text>
        <View style={styles.headerText}>
          <Text style={styles.eyebrow}>REBOOTFOCUS VALIDATION</Text>
          <Text style={styles.h1}>실패 이후를 다시 붙잡는 복귀 앱</Text>
        </View>
      </View>

      <StatusRail state={state} onStageChange={handleStageChange} />

      <View style={styles.workspace}>
        <ContextStage
          active={state.activeStage === 0}
          userId={state.userId}
          metricDate={state.metricDate}
          onSave={handleContextSave}
          onStageChange={handleStageChange}
        />
        <PlanStage
          active={state.activeStage === 1}
          metricDate={state.metricDate}
          inboxItems={state.inboxItems}
          big3Items={state.big3Items}
          executionUnits={state.executionUnits}
          timeboxes={state.timeboxes}
          pendingAction={pendingAction}
          onSaveInbox={handleSaveInbox}
          onSelectBig3={handleSelectBig3}
          onCreateExecutionUnits={handleCreateExecutionUnits}
          onAllocateTimeboxes={handleAllocateTimeboxes}
          onStageChange={handleStageChange}
        />
        <ExecuteStage
          active={state.activeStage === 2}
          timeboxes={state.timeboxes}
          executionUnits={state.executionUnits}
          activeSession={state.activeSession}
          latestFailure={state.latestFailure}
          latestFailureEventId={state.latestFailureEventId}
          pendingAction={pendingAction}
          onStartSession={handleStartSession}
          onCompleteSession={handleCompleteSession}
          onCompleteExecutionUnit={handleCompleteExecutionUnit}
          onInterruptSession={handleInterruptSession}
          onCheckInFailure={handleCheckInFailure}
          onRestart={handleRestart}
          onStageChange={handleStageChange}
        />
        <InsightStage
          active={state.activeStage === 3}
          recoveryOverview={state.recoveryOverview}
          batchOverview={state.batchOverview}
          alerts={state.alerts}
          alertsActiveOnly={state.alertsActiveOnly}
          pendingAction={pendingAction}
          onRefreshInsights={handleRefreshInsights}
          onToggleAlerts={handleToggleAlerts}
          onStageChange={handleStageChange}
        />
      </View>

      <Toast message={toastMessage} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#fff',
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
    backgroundColor: '#fff',
  },
  brandMark: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#111',
    color: '#fff',
    textAlign: 'center',
    lineHeight: 40,
    fontSize: 20,
    fontWeight: 'bold',
    marginRight: 12,
  },
  headerText: {
    flex: 1,
  },
  eyebrow: {
    fontSize: 10,
    fontWeight: 'bold',
    color: '#666',
    marginBottom: 2,
  },
  h1: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#111',
  },
  workspace: {
    flex: 1,
    backgroundColor: '#f9f9f9',
  },
});
