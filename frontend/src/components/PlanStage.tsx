import { useEffect, useState } from "react";
import type { AllocateTimeboxPayload, Big3Item, SavedInboxItem, TimeboxType } from "../types";
import { defaultLocalSlot, formatTimeRange, toIso } from "../utils";

interface PlanStageProps {
  active: boolean;
  metricDate: string;
  inboxItems: SavedInboxItem[];
  big3Items: Big3Item[];
  timeboxes: Array<{
    timeboxId: string;
    itemId: string;
    content: string;
    startAt: string;
    endAt: string;
    firstRecoveryBlock: boolean;
    type: TimeboxType;
  }>;
  pendingAction: string | null;
  onSaveInbox: (contents: string[]) => Promise<boolean>;
  onSelectBig3: (itemIds: string[]) => Promise<boolean>;
  onAllocateTimeboxes: (payload: AllocateTimeboxPayload[]) => Promise<boolean>;
  onStageChange: (stage: number) => void;
}

interface TimeboxDraft {
  itemId: string;
  startAt: string;
  endAt: string;
  firstRecoveryBlock: boolean;
  type: TimeboxType;
}

type PlanStep = "inbox" | "big3" | "timebox";

const planSteps: Array<{ id: PlanStep; title: string; description: string }> = [
  { id: "inbox", title: "Inbox", description: "머릿속 작업 저장" },
  { id: "big3", title: "Big 3", description: "오늘 붙잡을 3개" },
  { id: "timebox", title: "Timebox", description: "실행 블록 배정" }
];

export function PlanStage({
  active,
  metricDate,
  inboxItems,
  big3Items,
  timeboxes,
  pendingAction,
  onSaveInbox,
  onSelectBig3,
  onAllocateTimeboxes,
  onStageChange
}: PlanStageProps) {
  const [inboxText, setInboxText] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [timeboxDrafts, setTimeboxDrafts] = useState<TimeboxDraft[]>([]);
  const [activePlanStep, setActivePlanStep] = useState<PlanStep>("inbox");

  useEffect(() => {
    setSelectedIds((current) =>
      current.filter((itemId) => inboxItems.some((item) => item.id === itemId)).slice(0, 3)
    );
  }, [inboxItems]);

  useEffect(() => {
    setTimeboxDrafts((current) =>
      big3Items.map((item, index) => {
        const existing = current.find((draft) => draft.itemId === item.itemId);
        const defaults = defaultLocalSlot(metricDate, index);
        return (
          existing ?? {
            itemId: item.itemId,
            startAt: defaults.start,
            endAt: defaults.end,
            firstRecoveryBlock: index === 0,
            type: "WORK"
          }
        );
      })
    );
  }, [big3Items, metricDate]);

  const inboxContents = inboxText
    .split("\n")
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 20);

  return (
    <section className={`stage-panel ${active ? "is-active" : ""}`}>
      <section className="stage-intro reveal">
        <p className="eyebrow">02 PLAN</p>
        <h2>복귀를 위한 최소 계획만 남기고 바로 실행 가능한 상태로 만듭니다.</h2>
        <p className="section-note">긴 계획표 대신 Inbox, Big 3, 타임박스 세 단계만 유지합니다.</p>
      </section>

      <div className="step-tabs" aria-label="계획 세부 단계">
        {planSteps.map((step, index) => (
          <button
            className={`step-tab ${activePlanStep === step.id ? "is-active" : ""}`}
            type="button"
            key={step.id}
            onClick={() => setActivePlanStep(step.id)}
          >
            <span>{String(index + 1).padStart(2, "0")}</span>
            <strong>{step.title}</strong>
            <small>{step.description}</small>
          </button>
        ))}
      </div>

      <div className="plan-step-layout">
        {activePlanStep === "inbox" && (
          <article className="flow-card reveal">
          <div className="section-head">
            <div>
              <p className="eyebrow">BRAIN DUMP</p>
              <h2>브레인 덤프</h2>
            </div>
            <p className="section-note">한 줄에 하나씩 입력하면 Inbox Item으로 저장됩니다.</p>
          </div>
          <form
            className="stack-form"
            onSubmit={async (event) => {
              event.preventDefault();
              const saved = await onSaveInbox(inboxContents);
              if (saved) {
                setInboxText("");
                setActivePlanStep("big3");
              }
            }}
          >
            <label>
              <span>오늘 머릿속에 쌓인 일</span>
              <textarea
                rows={6}
                placeholder={"회의 정리\n보고서 초안\n운동 20분"}
                value={inboxText}
                onChange={(event) => setInboxText(event.target.value)}
              />
            </label>
            <button className="primary-button" type="submit" disabled={pendingAction === "inbox"}>
              Inbox 저장
            </button>
          </form>
          <InboxResult items={inboxItems} />
        </article>
        )}

        {activePlanStep === "big3" && (
          <article className="flow-card reveal">
          <div className="section-head">
            <div>
              <p className="eyebrow">TODAY'S FOCUS</p>
              <h2>오늘의 Big 3</h2>
            </div>
            <p className="section-note">저장된 항목 중 최대 3개를 선택합니다.</p>
          </div>
          <form
            className="stack-form"
            onSubmit={(event) => {
              event.preventDefault();
              void onSelectBig3(selectedIds.slice(0, 3)).then((selected) => {
                if (selected) {
                  setActivePlanStep("timebox");
                }
              });
            }}
          >
            <Big3Candidates
              inboxItems={inboxItems}
              selectedIds={selectedIds}
              onToggle={(itemId) => {
                setSelectedIds((current) => {
                  if (current.includes(itemId)) {
                    return current.filter((id) => id !== itemId);
                  }
                  return [...current, itemId].slice(0, 3);
                });
              }}
            />
            <button className="primary-button" type="submit" disabled={pendingAction === "big3"}>
              Big 3 확정
            </button>
          </form>
          <Big3Result items={big3Items} />
        </article>
        )}

        {activePlanStep === "timebox" && (
          <article className="flow-card reveal">
          <div className="section-head">
            <div>
              <p className="eyebrow">TIME DESIGN</p>
              <h2>타임박스 설계</h2>
            </div>
            <p className="section-note">첫 복귀 블록은 정확히 1개여야 합니다.</p>
          </div>
          <form
            className="stack-form"
            onSubmit={(event) => {
              event.preventDefault();
              void onAllocateTimeboxes(
                timeboxDrafts.map((draft) => ({
                  ...draft,
                  startAt: toIso(draft.startAt),
                  endAt: toIso(draft.endAt)
                }))
              );
            }}
          >
            <TimeboxBuilder
              items={big3Items}
              drafts={timeboxDrafts}
              onChange={(next) => setTimeboxDrafts(next)}
              metricDate={metricDate}
            />
            <button className="primary-button" type="submit" disabled={pendingAction === "timeboxes"}>
              타임박스 할당
            </button>
          </form>
          <TimeboxResult timeboxes={timeboxes} />
        </article>
        )}

        <aside className="plan-summary-panel">
          <div className="summary-stack">
            <div className="mini-summary">
              <span>Inbox</span>
              <strong>{inboxItems.length}</strong>
            </div>
            <div className="mini-summary">
              <span>Big 3</span>
              <strong>{big3Items.length}</strong>
            </div>
            <div className="mini-summary">
              <span>Timebox</span>
              <strong>{timeboxes.length}</strong>
            </div>
          </div>
          <div className="next-step-note">
            <strong>{planSteps.find((step) => step.id === activePlanStep)?.title}</strong>
            <p>
              {activePlanStep === "inbox" &&
                "먼저 할 일을 저장하면 Big 3 선택 화면으로 넘어갑니다."}
              {activePlanStep === "big3" &&
                "저장된 항목 중 오늘 다시 붙잡을 항목을 최대 3개 고릅니다."}
              {activePlanStep === "timebox" &&
                "선택한 Big 3를 실제 실행 시간으로 배정하면 실행 단계로 넘어갑니다."}
            </p>
          </div>
        </aside>
      </div>

      <div className="stage-footer">
        <button className="ghost-button" type="button" onClick={() => onStageChange(0)}>
          이전 단계
        </button>
        <button className="primary-button" type="button" onClick={() => onStageChange(2)}>
          실행 단계로 이동
        </button>
      </div>
    </section>
  );
}

function InboxResult({ items }: { items: SavedInboxItem[] }) {
  if (items.length === 0) {
    return <div className="result-list empty-state">아직 저장된 항목이 없습니다.</div>;
  }

  return (
    <div className="result-list item-list">
      {items.map((item) => (
        <div className="item-row" key={item.id}>
          <div>
            <div>{item.content}</div>
            <small className="meta-kicker">{item.id}</small>
          </div>
          <span className="chip">INBOX</span>
        </div>
      ))}
    </div>
  );
}

function Big3Candidates({
  inboxItems,
  selectedIds,
  onToggle
}: {
  inboxItems: SavedInboxItem[];
  selectedIds: string[];
  onToggle: (itemId: string) => void;
}) {
  if (inboxItems.length === 0) {
    return <div className="check-list empty-state">Inbox 저장 후 선택할 수 있습니다.</div>;
  }

  return (
    <div className="check-list selection-list">
      {inboxItems.map((item) => (
        <label className="selection-row" key={item.id}>
          <input
            type="checkbox"
            name="big3-item"
            value={item.id}
            checked={selectedIds.includes(item.id)}
            onChange={() => onToggle(item.id)}
          />
          <span>{item.content}</span>
        </label>
      ))}
    </div>
  );
}

function Big3Result({ items }: { items: Big3Item[] }) {
  if (items.length === 0) {
    return <div className="result-list empty-state">아직 선택된 Big 3가 없습니다.</div>;
  }

  return (
    <div className="result-list item-list">
      {items.map((item, index) => (
        <div className="item-row" key={item.itemId}>
          <div>
            <div>
              {index + 1}. {item.content}
            </div>
            <small className="meta-kicker">{item.itemId}</small>
          </div>
          <span className="chip">BIG 3</span>
        </div>
      ))}
    </div>
  );
}

function TimeboxBuilder({
  items,
  drafts,
  onChange,
  metricDate
}: {
  items: Big3Item[];
  drafts: TimeboxDraft[];
  onChange: (drafts: TimeboxDraft[]) => void;
  metricDate: string;
}) {
  if (items.length === 0) {
    return <div className="builder-list empty-state">Big 3를 고르면 자동으로 입력 폼이 생성됩니다.</div>;
  }

  const updateDraft = (itemId: string, patch: Partial<TimeboxDraft>) => {
    onChange(drafts.map((draft) => (draft.itemId === itemId ? { ...draft, ...patch } : draft)));
  };

  return (
    <div className="builder-list timebox-list">
      {items.map((item, index) => {
        const defaults = defaultLocalSlot(metricDate, index);
        const draft =
          drafts.find((candidate) => candidate.itemId === item.itemId) ?? {
            itemId: item.itemId,
            startAt: defaults.start,
            endAt: defaults.end,
            firstRecoveryBlock: index === 0,
            type: "WORK" as const
          };

        return (
          <div className="timebox-row" key={item.itemId}>
            <div className="timebox-meta">
              <span className="meta-kicker">ITEM {index + 1}</span>
              <strong>{item.content}</strong>
            </div>
            <label>
              <span>시작</span>
              <input
                name="startAt"
                type="datetime-local"
                value={draft.startAt}
                onChange={(event) => updateDraft(item.itemId, { startAt: event.target.value })}
              />
            </label>
            <label>
              <span>종료</span>
              <input
                name="endAt"
                type="datetime-local"
                value={draft.endAt}
                onChange={(event) => updateDraft(item.itemId, { endAt: event.target.value })}
              />
            </label>
            <div>
              <span>타입 / 첫 복귀</span>
              <div className="action-row">
                <select
                  name="type"
                  value={draft.type}
                  onChange={(event) => updateDraft(item.itemId, { type: event.target.value as TimeboxType })}
                >
                  <option value="WORK">WORK</option>
                  <option value="BREAK">BREAK</option>
                </select>
                <label className="chip">
                  <input
                    name="firstRecoveryBlock"
                    type="radio"
                    checked={draft.firstRecoveryBlock}
                    onChange={() =>
                      onChange(
                        drafts.map((candidate) => ({
                          ...candidate,
                          firstRecoveryBlock: candidate.itemId === item.itemId
                        }))
                      )
                    }
                  />
                  <span>첫 블록</span>
                </label>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function TimeboxResult({ timeboxes }: { timeboxes: PlanStageProps["timeboxes"] }) {
  if (timeboxes.length === 0) {
    return <div className="result-list empty-state">아직 생성된 타임박스가 없습니다.</div>;
  }

  return (
    <div className="result-list timebox-list">
      {timeboxes.map((timebox) => (
        <div className="item-row" key={timebox.timeboxId}>
          <div>
            <div>{timebox.content}</div>
            <small className="meta-kicker">
              {formatTimeRange(timebox.startAt, timebox.endAt)} / {timebox.type}
            </small>
          </div>
          <span className="chip">{timebox.firstRecoveryBlock ? "FIRST" : "FLOW"}</span>
        </div>
      ))}
    </div>
  );
}
