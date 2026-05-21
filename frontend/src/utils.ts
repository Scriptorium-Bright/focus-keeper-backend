import type { IsoDateTime } from "./types";

export function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

export function toIso(localDateTime: string): IsoDateTime {
  return new Date(localDateTime).toISOString();
}

export function defaultLocalSlot(metricDate: string, index: number) {
  const startHour = 9 + index;
  const endHour = startHour + 1;
  return {
    start: `${metricDate}T${String(startHour).padStart(2, "0")}:00`,
    end: `${metricDate}T${String(endHour).padStart(2, "0")}:00`
  };
}

export function formatTimeRange(startAt: string, endAt: string) {
  const start = new Date(startAt);
  const end = new Date(endAt);
  return `${pad(start.getHours())}:${pad(start.getMinutes())} - ${pad(end.getHours())}:${pad(end.getMinutes())}`;
}

export function pretty(value: unknown) {
  return JSON.stringify(value, null, 2);
}

export function boolText(value: boolean | null | undefined) {
  if (value == null) {
    return "-";
  }
  return value ? "Yes" : "No";
}

function pad(value: number) {
  return String(value).padStart(2, "0");
}
