export type IsoDate = string;
export type IsoDateTime = string;

export interface ApiSuccess<T> {
  success: true;
  data: T;
  message: string;
  traceId: string;
}

export interface ApiFailure {
  success: false;
  error?: {
    code?: string;
    message?: string;
    details?: unknown;
  };
  message?: string;
  traceId?: string;
}

export type ApiResponse<T> = ApiSuccess<T> | ApiFailure;

export interface SavedInboxItem {
  id: string;
  content: string;
  createdAt: IsoDateTime;
}

export interface SaveInboxItemsResponse {
  savedCount: number;
  savedItems: SavedInboxItem[];
}

export interface Big3Item {
  big3SelectionItemId: string;
  itemId: string;
  content: string;
  completionStatus?: string;
}

export interface SelectBig3Response {
  selectedDate: IsoDate;
  selectedAt: IsoDateTime;
  selectedCount: number;
  selectedItems: Big3Item[];
}

export type TimeboxType = "WORK" | "BREAK";

export interface CreateExecutionUnitPayload {
  big3SelectionItemId: string;
  title: string;
}

export interface UpdateExecutionUnitPayload {
  title: string;
}

export interface ExecutionUnit {
  executionUnitId: string;
  big3SelectionItemId: string;
  title: string;
  status?: string;
  completedAt?: IsoDateTime | null;
  createdAt: IsoDateTime;
}

export interface AllocateTimeboxPayload {
  executionUnitId: string;
  startAt: IsoDateTime;
  endAt: IsoDateTime;
  firstRecoveryBlock: boolean;
  type: TimeboxType;
}

export interface AllocatedTimebox {
  timeboxId: string;
  executionUnitId: string;
  content: string;
  startAt: IsoDateTime;
  endAt: IsoDateTime;
  firstRecoveryBlock: boolean;
  type: TimeboxType;
  createdAt: IsoDateTime;
}

export interface AllocateTimeboxesResponse {
  plannedDate: IsoDate;
  allocatedCount: number;
  timeboxes: AllocatedTimebox[];
}

export type RecoverySessionStatus = "STARTED" | "COMPLETED" | "INTERRUPTED" | string;

export interface RecoverySession {
  sessionId: string;
  timeboxId: string;
  status: RecoverySessionStatus;
  startedAt: IsoDateTime;
  endedAt: IsoDateTime | null;
  createdAt: IsoDateTime;
}

export type FailureReason =
  | "TOO_BIG"
  | "INTERRUPTION"
  | "LOW_ENERGY"
  | "UNCLEAR_NEXT_ACTION"
  | "CONTEXT_SWITCHED";

export interface RestartSuggestion {
  restartType: string;
  suggestedMinutes: number;
  message: string;
}

export interface FailureCheckInResponse {
  failureEventId: string;
  sessionId: string;
  timeboxId: string;
  reason: FailureReason | string;
  note: string | null;
  occurredAt: IsoDateTime;
  sessionStatus: RecoverySessionStatus;
  restartSuggestion: RestartSuggestion;
}

export interface RestartEvent {
  id: string;
  failureEventId: string;
  restartType: string;
  suggestedMinutes: number;
  occurredAt: IsoDateTime;
}

export interface RestartRecoveryResponse {
  restartEvent: RestartEvent;
  recoverySession: RecoverySession;
  restartSuggestion: RestartSuggestion;
}

export interface DailyKpi {
  dailyKpiId: string;
  userId: string;
  metricDate: IsoDate;
  activation: boolean;
  failureCount: number;
  recovery24: boolean;
  recovery48: boolean;
  restartCount24: number;
  restartCount48: number;
  ttrMinutes: number | null;
  cycleCompletionRate: number | null;
  planExecutionRate: number | null;
  plannedWorkMinutes: number;
  actualWorkMinutes: number;
  estimationErrorMinutes: number;
  generatedAt: IsoDateTime;
}

export interface DailyKpiQuality {
  dailyKpiQualityReportId: string;
  userId: string;
  metricDate: IsoDate;
  healthy: boolean;
  duplicateRestartLinkCount: number;
  orphanRestartCount: number;
  restartBeforeFailureCount: number;
  lateRestartLinkCount: number;
  breakSessionReferenceCount: number;
  missingTimeboxReferenceCount: number;
  timezoneMismatchCount: number;
  totalIssueCount: number;
  generatedAt: IsoDateTime;
}

export interface DailyKpiLastProcessedDate {
  pipelineKey: string;
  userId: string;
  lastProcessedDate: IsoDate;
  updatedAt: IsoDateTime;
}

export interface OperationsAlert {
  alertKey: string;
  pipelineKey: string;
  stage: string;
  userId: string;
  severity: string;
  active: boolean;
  status: string;
  summary: string;
  details: Record<string, string>;
  firstSeenAt: IsoDateTime | null;
  lastSeenAt: IsoDateTime | null;
  resolvedAt: IsoDateTime | null;
  occurrenceCount: number;
  reopenCount: number;
  lastChangedAt: IsoDateTime | null;
}

export interface FailureHourDistribution {
  reportId: string;
  userId: string;
  metricDate: IsoDate;
  totalFailureCount: number;
  peakFailureHour: number | null;
  peakFailureWindow: string | null;
  generatedAt: IsoDateTime;
  hourlyMetrics: Array<{
    localHour: number;
    failureCount: number;
    failureRatio: number;
    peakHour: boolean;
  }>;
}

export interface FrictionSignalReport {
  userId: string;
  metricDate: IsoDate;
  signals: Array<{
    signalType: string;
    active: boolean;
    evidenceCount: number;
    generatedAt: IsoDateTime;
  }>;
}

export interface FrictionSegmentReport {
  userId: string;
  metricDate: IsoDate;
  segments: Array<{
    segmentType: string;
    title: string;
    summary: string;
    evidence: string;
  }>;
}

export interface RecoveryLoopOverview {
  userId: string;
  metricDate: IsoDate;
  dailyKpi: DailyKpi | null;
  failureHour: FailureHourDistribution | null;
  frictionSignals: FrictionSignalReport | null;
  frictionSegments: FrictionSegmentReport | null;
  metricNames: string[];
  activeAlerts: OperationsAlert[];
}

export interface WeeklyRetrospective {
  retrospectiveId: string;
  weekStart: IsoDate;
  weekEnd: IsoDate;
  sessionStartedCount: number;
  sessionCompletedCount: number;
  sessionInterruptedCount: number;
  failureCount: number;
  restartCount: number;
  dominantFailureReason: string;
  summary: string;
  antiSlipAction: {
    actionCode: string;
    title: string;
    description: string;
  };
  generatedAt: IsoDateTime;
}

export interface BatchOverview {
  userId: string;
  metricDate: IsoDate;
  qualityReport: DailyKpiQuality | null;
  lastProcessedDate: DailyKpiLastProcessedDate | null;
  weeklyRetrospective: WeeklyRetrospective | null;
  metricNames: string[];
  activeAlerts: OperationsAlert[];
}
