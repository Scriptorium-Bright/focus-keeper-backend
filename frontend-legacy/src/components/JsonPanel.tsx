import { pretty } from "../utils";

interface JsonPanelProps {
  title: string;
  value: unknown;
  emptyText?: string;
}

export function JsonPanel({ title, value, emptyText = "아직 조회하지 않았습니다." }: JsonPanelProps) {
  return (
    <div className="json-panel">
      <h3>{title}</h3>
      <pre>{value == null ? emptyText : pretty(value)}</pre>
    </div>
  );
}
