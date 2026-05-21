import { afterEach, describe, expect, it, vi } from "vitest";
import { getAlerts, saveInboxItems } from "../api";

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
});
