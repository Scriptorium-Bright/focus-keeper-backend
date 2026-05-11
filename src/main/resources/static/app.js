const storageKey = "rebootfocus-validation-context";

const state = {
    userId: "",
    metricDate: "",
    activeStage: 0,
    alertsActiveOnly: true,
    inboxItems: [],
    big3Items: [],
    timeboxes: [],
    activeSession: null,
    latestFailureEventId: null,
    latestFailure: null
};

const el = {
    toast: document.getElementById("toast"),
    userId: document.getElementById("user-id"),
    metricDate: document.getElementById("metric-date"),
    statusUser: document.getElementById("status-user"),
    statusPlanCount: document.getElementById("status-plan-count"),
    statusTimeboxCount: document.getElementById("status-timebox-count"),
    statusSession: document.getElementById("status-session"),
    inboxItems: document.getElementById("inbox-items"),
    inboxResult: document.getElementById("inbox-result"),
    big3Candidates: document.getElementById("big3-candidates"),
    big3Result: document.getElementById("big3-result"),
    timeboxBuilder: document.getElementById("timebox-builder"),
    timeboxResult: document.getElementById("timebox-result"),
    sessionState: document.getElementById("session-state"),
    timeboxActions: document.getElementById("timebox-actions"),
    failureResult: document.getElementById("failure-result"),
    failureReason: document.getElementById("failure-reason"),
    failureNote: document.getElementById("failure-note"),
    completeButton: document.getElementById("complete-session-button"),
    interruptButton: document.getElementById("interrupt-session-button"),
    restartButton: document.getElementById("restart-button"),
    recoveryJson: document.getElementById("recovery-overview-json"),
    batchJson: document.getElementById("batch-overview-json"),
    alertsJson: document.getElementById("alerts-json"),
    alertsLifecycleView: document.getElementById("alerts-lifecycle-view"),
    summaryRecovery24: document.getElementById("summary-recovery24"),
    summaryTtr: document.getElementById("summary-ttr"),
    summaryCycle: document.getElementById("summary-cycle"),
    summaryLastProcessedDate: document.getElementById("summary-last-processed-date"),
    stageButtons: [...document.querySelectorAll("[data-stage]")],
    stagePanels: [...document.querySelectorAll("[data-stage-panel]")]
};

document.addEventListener("DOMContentLoaded", () => {
    hydrateContext();
    bindForms();
    bindStageNavigation();
    renderAll();
});

function bindStageNavigation() {
    el.stageButtons.forEach((button) => {
        button.addEventListener("click", () => {
            setStage(Number(button.dataset.stage));
        });
    });

    document.querySelectorAll("[data-go-stage]").forEach((button) => {
        button.addEventListener("click", () => {
            setStage(Number(button.dataset.goStage));
        });
    });
}

function bindForms() {
    document.getElementById("context-form").addEventListener("submit", (event) => {
        event.preventDefault();
        state.userId = el.userId.value.trim();
        state.metricDate = el.metricDate.value;
        persistContext();
        renderStatusStrip();
        setStage(1);
        toast("검증 컨텍스트를 저장했습니다.");
    });

    document.getElementById("inbox-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!safeEnsureContext()) {
            return;
        }

        const items = el.inboxItems.value
            .split("\n")
            .map((item) => item.trim())
            .filter(Boolean)
            .slice(0, 20)
            .map((content) => ({ content }));

        if (items.length === 0) {
            toast("한 줄 이상 입력해 주세요.");
            return;
        }

        const response = await api("/api/v1/recovery/inbox-items", {
            method: "POST",
            body: JSON.stringify({
                userId: state.userId,
                items
            })
        });

        state.inboxItems = response.data.savedItems;
        state.big3Items = [];
        state.timeboxes = [];
        state.activeSession = null;
        state.latestFailureEventId = null;
        state.latestFailure = null;
        el.inboxItems.value = "";
        renderAll();
        toast(`${response.data.savedCount}개의 Inbox Item을 저장했습니다.`);
    });

    document.getElementById("big3-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!safeEnsureContext()) {
            return;
        }

        const selectedIds = [...document.querySelectorAll("input[name='big3-item']:checked")]
            .map((checkbox) => checkbox.value)
            .slice(0, 3);

        if (selectedIds.length === 0) {
            toast("Big 3를 최소 1개 선택해 주세요.");
            return;
        }

        const response = await api("/api/v1/recovery/big3", {
            method: "POST",
            body: JSON.stringify({
                userId: state.userId,
                itemIds: selectedIds
            })
        });

        state.big3Items = response.data.selectedItems;
        state.timeboxes = [];
        state.activeSession = null;
        state.latestFailureEventId = null;
        state.latestFailure = null;
        renderAll();
        toast(`${response.data.selectedCount}개의 Big 3를 확정했습니다.`);
    });

    document.getElementById("timebox-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!safeEnsureContext()) {
            return;
        }

        const rows = [...document.querySelectorAll("[data-timebox-row]")];
        const payload = rows.map((row) => ({
            itemId: row.dataset.itemId,
            startAt: toIso(row.querySelector("input[name='startAt']").value),
            endAt: toIso(row.querySelector("input[name='endAt']").value),
            firstRecoveryBlock: row.querySelector("input[name='firstRecoveryBlock']").checked,
            type: row.querySelector("select[name='type']").value
        }));

        if (payload.length === 0) {
            toast("Big 3를 먼저 선택해 주세요.");
            return;
        }

        const firstRecoveryCount = payload.filter((item) => item.firstRecoveryBlock).length;
        if (firstRecoveryCount !== 1) {
            toast("첫 복귀 블록은 정확히 1개여야 합니다.");
            return;
        }

        const response = await api("/api/v1/recovery/timeboxes", {
            method: "POST",
            body: JSON.stringify({
                userId: state.userId,
                timeboxes: payload
            })
        });

        state.timeboxes = response.data.timeboxes;
        state.activeSession = null;
        state.latestFailureEventId = null;
        state.latestFailure = null;
        renderAll();
        toast(`${response.data.allocatedCount}개의 타임박스를 만들었습니다.`);
        setStage(2);
    });

    el.completeButton.addEventListener("click", async () => {
        if (!state.activeSession) {
            toast("먼저 세션을 시작해 주세요.");
            return;
        }
        const response = await api("/api/v1/recovery/sessions/complete", {
            method: "POST",
            body: JSON.stringify({
                userId: state.userId,
                sessionId: state.activeSession.sessionId
            })
        });
        state.activeSession = response.data;
        renderAll();
        toast("세션을 완료했습니다.");
    });

    el.interruptButton.addEventListener("click", async () => {
        if (!state.activeSession) {
            toast("먼저 세션을 시작해 주세요.");
            return;
        }
        const response = await api("/api/v1/recovery/sessions/interrupt", {
            method: "POST",
            body: JSON.stringify({
                userId: state.userId,
                sessionId: state.activeSession.sessionId
            })
        });
        state.activeSession = response.data;
        renderAll();
        toast("세션을 중단 상태로 바꿨습니다.");
    });

    document.getElementById("failure-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!state.activeSession) {
            toast("실패 체크인은 활성 세션이 있어야 합니다.");
            return;
        }

        const response = await api("/api/v1/recovery/failures/check-in", {
            method: "POST",
            body: JSON.stringify({
                userId: state.userId,
                sessionId: state.activeSession.sessionId,
                reason: el.failureReason.value,
                note: el.failureNote.value.trim()
            })
        });

        state.latestFailureEventId = response.data.failureEventId;
        state.latestFailure = response.data;
        renderAll();
        toast("실패 이벤트를 기록했습니다.");
    });

    el.restartButton.addEventListener("click", async () => {
        if (!state.latestFailureEventId) {
            toast("실패 체크인을 먼저 기록해 주세요.");
            return;
        }

        const response = await api("/api/v1/recovery/restarts", {
            method: "POST",
            body: JSON.stringify({
                userId: state.userId,
                failureEventId: state.latestFailureEventId
            })
        });

        state.activeSession = response.data.recoverySession;
        renderAll();
        toast("10분 재시작 세션을 만들었습니다.");
    });

    document.getElementById("refresh-insights-button").addEventListener("click", async () => {
        await refreshInsights();
    });

    const refreshAlertsButton = document.getElementById("refresh-alerts-button");
    refreshAlertsButton.addEventListener("click", async () => {
        if (!safeEnsureContext()) {
            return;
        }
        state.alertsActiveOnly = !state.alertsActiveOnly;
        const alerts = await api(`/api/v1/ops/alerts?userId=${encodeURIComponent(state.userId)}&activeOnly=${state.alertsActiveOnly}`);
        el.alertsJson.textContent = pretty(alerts.data);
        renderAlertLifecycleCards(alerts.data);
        refreshAlertsButton.textContent = state.alertsActiveOnly ? "전체 알림 보기" : "활성 알림만 보기";
        toast(state.alertsActiveOnly ? "활성 알림을 조회했습니다." : "전체 알림을 조회했습니다.");
    });
}

function hydrateContext() {
    const today = new Date().toISOString().slice(0, 10);
    const saved = JSON.parse(localStorage.getItem(storageKey) || "{}");

    state.userId = saved.userId || "demo-validation-user";
    state.metricDate = saved.metricDate || today;

    el.userId.value = state.userId;
    el.metricDate.value = state.metricDate;
}

function persistContext() {
    localStorage.setItem(storageKey, JSON.stringify({
        userId: state.userId,
        metricDate: state.metricDate
    }));
}

function ensureContext() {
    state.userId = el.userId.value.trim();
    state.metricDate = el.metricDate.value;
    if (!state.userId || !state.metricDate) {
        throw new Error("userId와 metricDate를 먼저 입력해 주세요.");
    }
    persistContext();
}

function safeEnsureContext() {
    try {
        ensureContext();
        return true;
    } catch (error) {
        toast(error.message || "컨텍스트를 먼저 입력해 주세요.");
        return false;
    }
}

async function refreshInsights() {
    if (!safeEnsureContext()) {
        return;
    }

    const recovery = await api(`/api/v1/ops/overview/recovery-loop?userId=${encodeURIComponent(state.userId)}&metricDate=${state.metricDate}`);
    const batch = await api(`/api/v1/ops/overview/batch?userId=${encodeURIComponent(state.userId)}&metricDate=${state.metricDate}`);
    const alerts = await api(`/api/v1/ops/alerts?userId=${encodeURIComponent(state.userId)}&activeOnly=${state.alertsActiveOnly}`);

    el.recoveryJson.textContent = pretty(recovery.data);
    el.batchJson.textContent = pretty(batch.data);
    el.alertsJson.textContent = pretty(alerts.data);
    renderAlertLifecycleCards(alerts.data);
    document.getElementById("refresh-alerts-button").textContent = state.alertsActiveOnly ? "전체 알림 보기" : "활성 알림만 보기";

    const dailyKpi = recovery.data.dailyKpi;
    el.summaryRecovery24.textContent = dailyKpi ? boolText(dailyKpi.recovery24) : "-";
    el.summaryTtr.textContent = dailyKpi?.ttrMinutes ?? "-";
    el.summaryCycle.textContent = dailyKpi?.cycleCompletionRate ?? "-";
    el.summaryLastProcessedDate.textContent = batch.data.lastProcessedDate?.lastProcessedDate ?? "-";

    setStage(3);
    toast("Recovery / Batch overview를 갱신했습니다.");
}

function renderAlertLifecycleCards(alerts) {
    if (!alerts || alerts.length === 0) {
        el.alertsLifecycleView.className = "result-list empty-state";
        el.alertsLifecycleView.textContent = "표시할 alert가 없습니다.";
        return;
    }

    el.alertsLifecycleView.className = "result-list item-list";
    el.alertsLifecycleView.innerHTML = alerts.map((alert) => `
        <div class="item-row">
            <div>
                <div>${escapeHtml(alert.summary)}</div>
                <small class="meta-kicker">
                    ${escapeHtml(alert.status)} · ${escapeHtml(alert.severity)} · occurrence ${alert.occurrenceCount} · reopen ${alert.reopenCount}
                </small>
                <small class="meta-kicker">
                    firstSeen ${escapeHtml(alert.firstSeenAt || "-")} / resolved ${escapeHtml(alert.resolvedAt || "-")}
                </small>
            </div>
            <span class="chip">${escapeHtml(alert.stage)}</span>
        </div>
    `).join("");
}

async function api(url, options = {}) {
    try {
        const response = await fetch(url, {
            headers: {
                "Content-Type": "application/json"
            },
            ...options
        });

        const payload = await response.json();
        if (!response.ok || payload.success === false) {
            const message = payload?.error?.message || payload?.message || "요청 처리 중 오류가 발생했습니다.";
            throw new Error(message);
        }
        return payload;
    } catch (error) {
        toast(error.message || "알 수 없는 오류가 발생했습니다.");
        throw error;
    }
}

function renderAll() {
    renderStatusStrip();
    renderStage();
    renderInbox();
    renderBig3();
    renderTimeboxBuilder();
    renderTimeboxes();
    renderSession();
    renderFailure();
}

function renderStatusStrip() {
    el.statusUser.textContent = state.userId || "-";
    el.statusPlanCount.textContent = `${state.inboxItems.length} / ${state.big3Items.length}`;
    el.statusTimeboxCount.textContent = String(state.timeboxes.length);
    el.statusSession.textContent = state.activeSession?.status || "대기 중";
}

function renderStage() {
    el.stageButtons.forEach((button) => {
        button.classList.toggle("is-active", Number(button.dataset.stage) === state.activeStage);
    });
    el.stagePanels.forEach((panel, index) => {
        panel.classList.toggle("is-active", index === state.activeStage);
    });
}

function setStage(stageIndex) {
    state.activeStage = stageIndex;
    renderStage();
    document.querySelector(".workspace")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderInbox() {
    if (state.inboxItems.length === 0) {
        el.inboxResult.className = "result-list empty-state";
        el.inboxResult.textContent = "아직 저장된 항목이 없습니다.";
        return;
    }

    el.inboxResult.className = "result-list item-list";
    el.inboxResult.innerHTML = state.inboxItems.map((item) => `
        <div class="item-row">
            <div>
                <div>${escapeHtml(item.content)}</div>
                <small class="meta-kicker">${escapeHtml(item.id)}</small>
            </div>
            <span class="chip">INBOX</span>
        </div>
    `).join("");
}

function renderBig3() {
    if (state.inboxItems.length === 0) {
        el.big3Candidates.className = "check-list empty-state";
        el.big3Candidates.textContent = "Inbox 저장 후 선택할 수 있습니다.";
    } else {
        el.big3Candidates.className = "check-list selection-list";
        el.big3Candidates.innerHTML = state.inboxItems.map((item) => {
            const checked = state.big3Items.some((selected) => selected.id === item.id) ? "checked" : "";
            return `
                <label class="selection-row">
                    <input type="checkbox" name="big3-item" value="${escapeHtml(item.id)}" ${checked}>
                    <span>${escapeHtml(item.content)}</span>
                </label>
            `;
        }).join("");
    }

    if (state.big3Items.length === 0) {
        el.big3Result.className = "result-list empty-state";
        el.big3Result.textContent = "아직 선택된 Big 3가 없습니다.";
        return;
    }

    el.big3Result.className = "result-list item-list";
    el.big3Result.innerHTML = state.big3Items.map((item, index) => `
        <div class="item-row">
            <div>
                <div>${index + 1}. ${escapeHtml(item.content)}</div>
                <small class="meta-kicker">${escapeHtml(item.id)}</small>
            </div>
            <span class="chip">BIG 3</span>
        </div>
    `).join("");
}

function renderTimeboxBuilder() {
    if (state.big3Items.length === 0) {
        el.timeboxBuilder.className = "builder-list empty-state";
        el.timeboxBuilder.textContent = "Big 3를 고르면 자동으로 입력 폼이 생성됩니다.";
        return;
    }

    el.timeboxBuilder.className = "builder-list timebox-list";
    el.timeboxBuilder.innerHTML = state.big3Items.map((item, index) => {
        const defaults = defaultLocalSlot(state.metricDate, index);
        return `
            <div class="timebox-row" data-timebox-row data-item-id="${escapeHtml(item.id)}">
                <div class="timebox-meta">
                    <span class="meta-kicker">ITEM ${index + 1}</span>
                    <strong>${escapeHtml(item.content)}</strong>
                </div>
                <label>
                    <span>시작</span>
                    <input name="startAt" type="datetime-local" value="${defaults.start}">
                </label>
                <label>
                    <span>종료</span>
                    <input name="endAt" type="datetime-local" value="${defaults.end}">
                </label>
                <div>
                    <span>타입 / 첫 복귀</span>
                    <div class="action-row">
                        <select name="type">
                            <option value="WORK">WORK</option>
                            <option value="BREAK">BREAK</option>
                        </select>
                        <label class="chip">
                            <input name="firstRecoveryBlock" type="radio" ${index === 0 ? "checked" : ""}>
                            <span>첫 블록</span>
                        </label>
                    </div>
                </div>
            </div>
        `;
    }).join("");

    const radios = [...document.querySelectorAll("input[name='firstRecoveryBlock']")];
    radios.forEach((radio) => {
        radio.addEventListener("change", () => {
            radios.forEach((other) => {
                if (other !== radio) {
                    other.checked = false;
                }
            });
        });
    });
}

function renderTimeboxes() {
    if (state.timeboxes.length === 0) {
        el.timeboxResult.className = "result-list empty-state";
        el.timeboxResult.textContent = "아직 생성된 타임박스가 없습니다.";
        return;
    }

    el.timeboxResult.className = "result-list timebox-list";
    el.timeboxResult.innerHTML = state.timeboxes.map((timebox) => `
        <div class="item-row">
            <div>
                <div>${escapeHtml(timebox.content)}</div>
                <small class="meta-kicker">${formatTimeRange(timebox.startAt, timebox.endAt)} / ${escapeHtml(timebox.type)}</small>
            </div>
            <span class="chip">${timebox.firstRecoveryBlock ? "FIRST" : "FLOW"}</span>
        </div>
    `).join("");
}

function renderSession() {
    if (!state.activeSession) {
        el.sessionState.className = "status-card empty-state";
        el.sessionState.textContent = "아직 시작된 세션이 없습니다.";
    } else {
        el.sessionState.className = "status-card";
        el.sessionState.innerHTML = `
            <strong>${escapeHtml(state.activeSession.status)}</strong>
            <div class="meta-kicker">sessionId: ${escapeHtml(state.activeSession.sessionId)}</div>
            <div class="meta-kicker">timeboxId: ${escapeHtml(state.activeSession.timeboxId)}</div>
        `;
    }

    if (state.timeboxes.length === 0) {
        el.timeboxActions.className = "chip-list empty-state";
        el.timeboxActions.textContent = "타임박스 할당 후 시작 버튼이 보입니다.";
        return;
    }

    el.timeboxActions.className = "chip-list";
    el.timeboxActions.innerHTML = state.timeboxes.map((timebox) => `
        <button class="ghost-button" type="button" data-start-timebox="${escapeHtml(timebox.timeboxId)}">
            ${escapeHtml(timebox.content)}
        </button>
    `).join("");

    document.querySelectorAll("[data-start-timebox]").forEach((button) => {
        button.addEventListener("click", async () => {
            const timeboxId = button.dataset.startTimebox;
            const response = await api("/api/v1/recovery/sessions/start", {
                method: "POST",
                body: JSON.stringify({
                    userId: state.userId,
                    timeboxId
                })
            });
            state.activeSession = response.data;
            renderAll();
            toast("복귀 세션을 시작했습니다.");
        });
    });
}

function renderFailure() {
    if (!state.latestFailure) {
        el.failureResult.className = "result-list empty-state";
        el.failureResult.textContent = "실패 이벤트와 재시작 결과가 여기에 표시됩니다.";
        return;
    }

    el.failureResult.className = "result-list alert-list";
    el.failureResult.innerHTML = `
        <div class="alert-row">
            <strong>${escapeHtml(state.latestFailure.reason)}</strong>
            <div>${escapeHtml(state.latestFailure.note || "메모 없음")}</div>
            <small class="meta-kicker">failureEventId: ${escapeHtml(state.latestFailure.failureEventId)}</small>
        </div>
    `;
}

function defaultLocalSlot(metricDate, index) {
    const startHour = 9 + index;
    const endHour = startHour + 1;
    return {
        start: `${metricDate}T${String(startHour).padStart(2, "0")}:00`,
        end: `${metricDate}T${String(endHour).padStart(2, "0")}:00`
    };
}

function toIso(localDateTime) {
    return new Date(localDateTime).toISOString();
}

function formatTimeRange(startAt, endAt) {
    const start = new Date(startAt);
    const end = new Date(endAt);
    return `${pad(start.getHours())}:${pad(start.getMinutes())} - ${pad(end.getHours())}:${pad(end.getMinutes())}`;
}

function pad(value) {
    return String(value).padStart(2, "0");
}

function pretty(value) {
    return JSON.stringify(value, null, 2);
}

function boolText(value) {
    return value ? "Yes" : "No";
}

function toast(message) {
    el.toast.textContent = message;
    el.toast.classList.add("show");
    clearTimeout(toast.timeoutId);
    toast.timeoutId = setTimeout(() => {
        el.toast.classList.remove("show");
    }, 2200);
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}
