import type {
  AllocatedTimebox,
  BatchOverview,
  Big3Item,
  ExecutionUnit,
  FailureCheckInResponse,
  OperationsAlert,
  RecoveryLoopOverview,
  RecoverySession,
  SavedInboxItem
} from "./types";
import { todayIsoDate } from "./utils";

export const storageKey = "rebootfocus-validation-context";

export interface WorkflowState {
  userId: string;
  metricDate: string;
  activeStage: number;
  alertsActiveOnly: boolean;
  inboxItems: SavedInboxItem[];
  big3Items: Big3Item[];
  executionUnits: ExecutionUnit[];
  timeboxes: AllocatedTimebox[];
  activeSession: RecoverySession | null;
  latestFailureEventId: string | null;
  latestFailure: FailureCheckInResponse | null;
  recoveryOverview: RecoveryLoopOverview | null;
  batchOverview: BatchOverview | null;
  alerts: OperationsAlert[] | null;
}

export type WorkflowAction =
  | { type: "contextSaved"; userId: string; metricDate: string }
  | { type: "stageChanged"; stage: number }
  | { type: "inboxSaved"; items: SavedInboxItem[] }
  | { type: "big3Selected"; items: Big3Item[] }
  | { type: "executionUnitsCreated"; units: ExecutionUnit[] }
  | { type: "timeboxesAllocated"; timeboxes: AllocatedTimebox[] }
  | { type: "sessionChanged"; session: RecoverySession }
  | { type: "failureCheckedIn"; failure: FailureCheckInResponse }
  | { type: "alertsModeChanged"; activeOnly: boolean }
  | {
      type: "insightsLoaded";
      recoveryOverview: RecoveryLoopOverview;
      batchOverview: BatchOverview;
      alerts: OperationsAlert[];
    }
  | { type: "alertsLoaded"; alerts: OperationsAlert[] };

export function createInitialWorkflowState(): WorkflowState {
  const saved = readSavedContext();
  return {
    userId: saved.userId,
    metricDate: saved.metricDate,
    activeStage: 0,
    alertsActiveOnly: true,
    inboxItems: [],
    big3Items: [],
    executionUnits: [],
    timeboxes: [],
    activeSession: null,
    latestFailureEventId: null,
    latestFailure: null,
    recoveryOverview: null,
    batchOverview: null,
    alerts: null
  };
}

export function workflowReducer(state: WorkflowState, action: WorkflowAction): WorkflowState {
  switch (action.type) {
    case "contextSaved":
      return {
        ...state,
        userId: action.userId,
        metricDate: action.metricDate,
        activeStage: 1
      };
    case "stageChanged":
      return { ...state, activeStage: action.stage };
    case "inboxSaved":
      return {
        ...state,
        inboxItems: action.items,
        big3Items: [],
        executionUnits: [],
        timeboxes: [],
        activeSession: null,
        latestFailureEventId: null,
        latestFailure: null
      };
    case "big3Selected":
      return {
        ...state,
        big3Items: action.items,
        executionUnits: [],
        timeboxes: [],
        activeSession: null,
        latestFailureEventId: null,
        latestFailure: null
      };
    case "executionUnitsCreated":
      return {
        ...state,
        executionUnits: action.units,
        timeboxes: [],
        activeSession: null,
        latestFailureEventId: null,
        latestFailure: null
      };
    case "timeboxesAllocated":
      return {
        ...state,
        timeboxes: action.timeboxes,
        activeSession: null,
        latestFailureEventId: null,
        latestFailure: null,
        activeStage: 2
      };
    case "sessionChanged":
      return { ...state, activeSession: action.session };
    case "failureCheckedIn":
      return {
        ...state,
        latestFailure: action.failure,
        latestFailureEventId: action.failure.failureEventId,
        activeSession: state.activeSession
          ? { ...state.activeSession, status: action.failure.sessionStatus }
          : state.activeSession
      };
    case "alertsModeChanged":
      return { ...state, alertsActiveOnly: action.activeOnly };
    case "insightsLoaded":
      return {
        ...state,
        recoveryOverview: action.recoveryOverview,
        batchOverview: action.batchOverview,
        alerts: action.alerts,
        activeStage: 3
      };
    case "alertsLoaded":
      return { ...state, alerts: action.alerts };
    default:
      return state;
  }
}

export function persistContext(userId: string, metricDate: string) {
  window.localStorage.setItem(storageKey, JSON.stringify({ userId, metricDate }));
}

function readSavedContext() {
  try {
    const saved = JSON.parse(window.localStorage.getItem(storageKey) ?? "{}") as Partial<{
      userId: string;
      metricDate: string;
    }>;
    return {
      userId: saved.userId ?? "demo-validation-user",
      metricDate: saved.metricDate ?? todayIsoDate()
    };
  } catch {
    return {
      userId: "demo-validation-user",
      metricDate: todayIsoDate()
    };
  }
}
