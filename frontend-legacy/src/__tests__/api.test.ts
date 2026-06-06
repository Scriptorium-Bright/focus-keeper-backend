import { afterEach, describe, expect, it, vi } from "vitest";
import {
  allocateTimeboxes,
  completeExecutionUnit,
  createExecutionUnit,
  getAlerts,
  saveInboxItems
} from "../api";

describe("api client", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns the typed data body from successful API responses", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        data: {
          savedCount: 1,
          savedItems: [{ id: "item-1", content: "보고서", createdAt: "2026-05-13T00:00:00Z" }]
        },
        message: "INBOX_ITEMS_SAVED",
        traceId: "trace-1"
      })
    } as Response);

    await expect(saveInboxItems("demo-user", ["보고서"])).resolves.toEqual({
      savedCount: 1,
      savedItems: [{ id: "item-1", content: "보고서", createdAt: "2026-05-13T00:00:00Z" }]
    });

    expect(fetch).toHaveBeenCalledWith(
      "/api/v1/recovery/inbox-items",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ userId: "demo-user", items: [{ content: "보고서" }] })
      })
    );
  });

  it("throws the backend error message when requests fail", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
      ok: false,
      json: async () => ({
        success: false,
        error: { code: "COMMON_BAD_REQUEST", message: "metricDate가 올바르지 않습니다." },
        traceId: "trace-1"
      })
    } as Response);

    await expect(getAlerts("demo-user", true)).rejects.toThrow("metricDate가 올바르지 않습니다.");
  });

  it("creates execution units under Big3 Big3 items", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        data: {
          executionUnitId: "unit-1",
          big3ItemId: "big3-item-1",
          title: "보고서 목차 잡기",
          createdAt: "2026-05-13T00:00:00Z"
        },
        message: "EXECUTION_UNIT_CREATED",
        traceId: "trace-1"
      })
    } as Response);

    await expect(
      createExecutionUnit("demo-user", {
        big3ItemId: "big3-item-1",
        title: "보고서 목차 잡기"
      })
    ).resolves.toMatchObject({
      executionUnitId: "unit-1",
      big3ItemId: "big3-item-1"
    });

    expect(fetch).toHaveBeenCalledWith(
      "/api/v1/recovery/execution-units",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          userId: "demo-user",
          big3ItemId: "big3-item-1",
          title: "보고서 목차 잡기"
        })
      })
    );
  });

  it("allocates timeboxes with executionUnitId", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        data: {
          plannedDate: "2026-05-13",
          allocatedCount: 1,
          timeboxes: [
            {
              timeboxId: "timebox-1",
              executionUnitId: "unit-1",
              content: "보고서 목차 잡기",
              startAt: "2026-05-13T00:00:00Z",
              endAt: "2026-05-13T01:00:00Z",
              firstRecoveryBlock: true,
              type: "WORK",
              createdAt: "2026-05-13T00:00:00Z"
            }
          ]
        },
        message: "TIMEBOXES_ALLOCATED",
        traceId: "trace-1"
      })
    } as Response);

    await allocateTimeboxes("demo-user", [
      {
        executionUnitId: "unit-1",
        startAt: "2026-05-13T00:00:00Z",
        endAt: "2026-05-13T01:00:00Z",
        firstRecoveryBlock: true,
        type: "WORK"
      }
    ]);

    expect(fetch).toHaveBeenCalledWith(
      "/api/v1/recovery/timeboxes",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          userId: "demo-user",
          timeboxes: [
            {
              executionUnitId: "unit-1",
              startAt: "2026-05-13T00:00:00Z",
              endAt: "2026-05-13T01:00:00Z",
              firstRecoveryBlock: true,
              type: "WORK"
            }
          ]
        })
      })
    );
  });

  it("completes execution units separately from sessions", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        data: {
          executionUnitId: "unit-1",
          big3ItemId: "big3-item-1",
          title: "보고서 목차 잡기",
          status: "COMPLETED",
          completedAt: "2026-05-13T01:00:00Z",
          createdAt: "2026-05-13T00:00:00Z"
        },
        message: "EXECUTION_UNIT_COMPLETED",
        traceId: "trace-1"
      })
    } as Response);

    await expect(completeExecutionUnit("demo-user", "unit-1")).resolves.toMatchObject({
      executionUnitId: "unit-1",
      status: "COMPLETED"
    });

    expect(fetch).toHaveBeenCalledWith(
      "/api/v1/recovery/execution-units/unit-1/complete",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ userId: "demo-user" })
      })
    );
  });
});
