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
      big3Items: [{ itemId: "item-1", content: "old focus" }],
      timeboxes: [
        {
          timeboxId: "timebox-1",
          itemId: "item-1",
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
    expect(next.timeboxes).toEqual([]);
    expect(next.activeSession).toBeNull();
  });

  it("uses defaults when storage is invalid", () => {
    window.localStorage.setItem(storageKey, "{not-json");

    const state = createInitialWorkflowState();

    expect(state.userId).toBe("demo-validation-user");
    expect(state.metricDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
