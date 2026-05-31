import AsyncStorage from '@react-native-async-storage/async-storage';
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
  isLoaded: boolean;
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
  | { type: "contextLoaded"; userId: string; metricDate: string }
  | { type: "contextSaved"; userId: string; metricDate: string }
  | { type: "stageChanged"; stage: number }
  | { type: "inboxSaved"; items: SavedInboxItem[] }
  | { type: "big3Selected"; items: Big3Item[] }
  | { type: "executionUnitsCreated"; units: ExecutionUnit[] }
  | { type: "executionUnitChanged"; unit: ExecutionUnit }
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
  return {
    isLoaded: false,
    userId: "",
    metricDate: "",
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
    case "contextLoaded":
      return {
        ...state,
        isLoaded: true,
        userId: action.userId,
        metricDate: action.metricDate,
      };
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
    case "executionUnitChanged":
      return {
        ...state,
        executionUnits: state.executionUnits.map((unit) =>
          unit.executionUnitId === action.unit.executionUnitId ? action.unit : unit
        )
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

export async function persistContext(userId: string, metricDate: string) {
  try {
    await AsyncStorage.setItem(storageKey, JSON.stringify({ userId, metricDate }));
  } catch (e) {
    console.error("Failed to save context to AsyncStorage", e);
  }
}

export async function readSavedContext() {
  try {
    const savedString = await AsyncStorage.getItem(storageKey);
    const saved = JSON.parse(savedString ?? "{}") as Partial<{
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
