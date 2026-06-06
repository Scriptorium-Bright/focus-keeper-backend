import { afterEach, describe, expect, it } from "vitest";
import {
  createInitialWorkflowState,
  persistContext,
  storageKey,
  workflowReducer
} from "../state";

describe("workflow state", () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it("hydrates context from localStorage", () => {
    window.localStorage.setItem(storageKey, JSON.stringify({ userId: "demo-user", metricDate: "2026-05-13" }));

    expect(createInitialWorkflowState()).toMatchObject({
      userId: "demo-user",
      metricDate: "2026-05-13",
      activeStage: 0
    });
  });

  it("persists context in the backend-compatible storage key", () => {
    persistContext("next-user", "2026-05-14");

    expect(JSON.parse(window.localStorage.getItem(storageKey) ?? "{}")).toEqual({
      userId: "next-user",
      metricDate: "2026-05-14"
    });
  });

  it("resets downstream state after saving a new inbox", () => {
    const state = {
      ...createInitialWorkflowState(),
      big3Items: [
        {
          big3ItemId: "big3-item-1",
          itemId: "item-1",
          content: "old focus"
        }
      ],
      executionUnits: [
        {
          executionUnitId: "unit-1",
          big3ItemId: "big3-item-1",
          title: "old unit",
          createdAt: "2026-05-13T00:00:00Z"
        }
      ],
      timeboxes: [
        {
          timeboxId: "timebox-1",
          executionUnitId: "unit-1",
          content: "old focus",
          startAt: "2026-05-13T00:00:00Z",
          endAt: "2026-05-13T01:00:00Z",
          firstRecoveryBlock: true,
          type: "WORK" as const,
          createdAt: "2026-05-13T00:00:00Z"
        }
      ],
      activeSession: {
        sessionId: "session-1",
        timeboxId: "timebox-1",
        status: "STARTED",
        startedAt: "2026-05-13T00:00:00Z",
        endedAt: null,
        createdAt: "2026-05-13T00:00:00Z"
      }
    };

    const next = workflowReducer(state, {
      type: "inboxSaved",
      items: [{ id: "item-2", content: "new focus", createdAt: "2026-05-13T00:00:00Z" }]
    });

    expect(next.inboxItems).toHaveLength(1);
    expect(next.big3Items).toEqual([]);
    expect(next.executionUnits).toEqual([]);
    expect(next.timeboxes).toEqual([]);
    expect(next.activeSession).toBeNull();
  });

  it("clears timeboxes when execution units are recreated", () => {
    const state = {
      ...createInitialWorkflowState(),
      timeboxes: [
        {
          timeboxId: "timebox-1",
          executionUnitId: "unit-1",
          content: "old unit",
          startAt: "2026-05-13T00:00:00Z",
          endAt: "2026-05-13T01:00:00Z",
          firstRecoveryBlock: true,
          type: "WORK" as const,
          createdAt: "2026-05-13T00:00:00Z"
        }
      ]
    };

    const next = workflowReducer(state, {
      type: "executionUnitsCreated",
      units: [
        {
          executionUnitId: "unit-2",
          big3ItemId: "big3-item-2",
          title: "new unit",
          createdAt: "2026-05-13T00:00:00Z"
        }
      ]
    });

    expect(next.executionUnits).toHaveLength(1);
    expect(next.timeboxes).toEqual([]);
  });

  it("updates a completed execution unit without changing session state", () => {
    const state = {
      ...createInitialWorkflowState(),
      executionUnits: [
        {
          executionUnitId: "unit-1",
          big3ItemId: "big3-item-1",
          title: "old unit",
          status: "PLANNED",
          completedAt: null,
          createdAt: "2026-05-13T00:00:00Z"
        }
      ],
      activeSession: {
        sessionId: "session-1",
        timeboxId: "timebox-1",
        status: "STARTED",
        startedAt: "2026-05-13T00:00:00Z",
        endedAt: null,
        createdAt: "2026-05-13T00:00:00Z"
      }
    };

    const next = workflowReducer(state, {
      type: "executionUnitChanged",
      unit: {
        executionUnitId: "unit-1",
        big3ItemId: "big3-item-1",
        title: "old unit",
        status: "COMPLETED",
        completedAt: "2026-05-13T01:00:00Z",
        createdAt: "2026-05-13T00:00:00Z"
      }
    });

    expect(next.executionUnits[0].status).toBe("COMPLETED");
    expect(next.activeSession?.status).toBe("STARTED");
  });

  it("uses defaults when storage is invalid", () => {
    window.localStorage.setItem(storageKey, "{not-json");

    const state = createInitialWorkflowState();

    expect(state.userId).toBe("demo-validation-user");
    expect(state.metricDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
