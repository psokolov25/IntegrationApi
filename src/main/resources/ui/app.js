const defaultStudioOperationsCatalog = [
    {operation: "FLUSH_OUTBOX", description: "Повторно отправить pending/failed outbox-сообщения", parameterTemplate: {limit: 100}},
    {operation: "RECOVER_STALE_INBOX", description: "Перевести stale PROCESSING inbox-записи в FAILED", parameterTemplate: {}},
    {operation: "CLEAR_DEBUG_HISTORY", description: "Очистить debug history (весь или по scriptId)", parameterTemplate: {scriptId: ""}},
    {operation: "REFRESH_BOOTSTRAP", description: "Получить свежий studio bootstrap snapshot", parameterTemplate: {debugHistoryLimit: 20}},
    {
        operation: "EXPORT_HTTP_PROCESSING_PROFILE",
        description: "Экспорт профиля programmable HTTP processing",
        parameterTemplate: {}
    },
    {
        operation: "IMPORT_HTTP_PROCESSING_PROFILE_PREVIEW",
        description: "Предпросмотр импортируемого профиля programmable HTTP processing",
        parameterTemplate: {
            httpProcessingProfile: {
                enabled: true,
                addDirectionHeader: true,
                directionHeaderName: "X-Integration-Direction",
                requestEnvelopeEnabled: true,
                parseJsonBody: true,
                responseBodyMaxChars: 2000
            }
        }
    },
    {
        operation: "IMPORT_HTTP_PROCESSING_PROFILE_APPLY",
        description: "Применение импортируемого профиля programmable HTTP processing",
        parameterTemplate: {
            httpProcessingProfile: {
                enabled: true,
                addDirectionHeader: true,
                directionHeaderName: "X-Integration-Direction",
                requestEnvelopeEnabled: true,
                parseJsonBody: true,
                responseBodyMaxChars: 2000
            }
        }
    },
    {
        operation: "PREVIEW_HTTP_PROCESSING",
        description: "Предпросмотр programmable HTTP processing (headers/body/response)",
        parameterTemplate: {
            direction: "OUTBOUND_EXTERNAL",
            headers: {"X-Trace": "demo"},
            body: {demo: true},
            responseStatus: 200,
            responseBody: "{\"ok\":true}",
            responseHeaders: {"Content-Type": ["application/json"]}
        }
    },
    {
        operation: "PREVIEW_HTTP_PROCESSING_MATRIX",
        description: "Сравнить обработку HTTP по всем направлениям (наружу/внутрь СУО)",
        parameterTemplate: {
            headers: {"X-Trace": "demo"},
            body: {demo: true},
            responseStatus: 200,
            responseBody: "{\"ok\":true}",
            responseHeaders: {"Content-Type": ["application/json"]}
        }
    },
    {
        operation: "PREVIEW_CONNECTOR_PROFILE",
        description: "Предпросмотр профиля broker/шины для GUI настройки",
        parameterTemplate: {
            brokerType: "KAFKA"
        }
    },
    {
        operation: "VALIDATE_CONNECTOR_CONFIG",
        description: "Проверка свойств connector по broker profile",
        parameterTemplate: {
            brokerType: "WEBHOOK_HTTP",
            properties: {
                url: "https://gateway.customer.local/integration/events",
                method: "POST"
            }
        }
    },
    {
        operation: "EXPORT_CONNECTOR_PRESETS",
        description: "Экспорт presets коннекторов (REST/broker/profiles)",
        parameterTemplate: {}
    },
    {
        operation: "IMPORT_CONNECTOR_PRESETS_PREVIEW",
        description: "Dry-run проверка импортируемых presets коннекторов",
        parameterTemplate: {
            messageBrokers: [
                {id: "webhook-bus", type: "WEBHOOK_HTTP", properties: {url: "https://gateway.customer.local/events"}}
            ],
            externalRestServices: [
                {id: "crm", baseUrl: "https://crm.customer.local"}
            ]
        }
    },
    {
        operation: "IMPORT_CONNECTOR_PRESETS_APPLY",
        description: "Применение импортируемых presets (после preview)",
        parameterTemplate: {
            replaceExisting: false,
            messageBrokers: [
                {id: "webhook-bus", type: "WEBHOOK_HTTP", properties: {url: "https://gateway.customer.local/events"}}
            ],
            externalRestServices: [
                {id: "crm", baseUrl: "https://crm.customer.local"}
            ]
        }
    },
    {
        operation: "IMPORT_CONNECTOR_PRESETS_DIFF",
        description: "Сравнение импортируемых presets с текущим конфигом",
        parameterTemplate: {
            messageBrokers: [
                {id: "webhook-bus", type: "WEBHOOK_HTTP", properties: {url: "https://gateway.customer.local/events"}}
            ],
            externalRestServices: [
                {id: "crm", baseUrl: "https://crm.customer.local"}
            ]
        }
    },
    {
        operation: "EXPORT_INTEGRATION_CONNECTOR_BUNDLE",
        description: "Экспорт единого bundle (HTTP processing + connectors)",
        parameterTemplate: {}
    },
    {
        operation: "IMPORT_INTEGRATION_CONNECTOR_BUNDLE_PREVIEW",
        description: "Dry-run импорт bundle интеграции без применения",
        parameterTemplate: {
            bundle: {
                httpProcessingProfile: {
                    enabled: true,
                    addDirectionHeader: true,
                    directionHeaderName: "X-Integration-Direction",
                    requestEnvelopeEnabled: true,
                    parseJsonBody: true,
                    responseBodyMaxChars: 2000
                },
                connectorPresets: {
                    messageBrokers: [
                        {id: "webhook-bus", type: "WEBHOOK_HTTP", properties: {url: "https://gateway.customer.local/events"}}
                    ],
                    externalRestServices: [
                        {id: "crm", baseUrl: "https://crm.customer.local"}
                    ]
                }
            }
        }
    },
    {
        operation: "IMPORT_INTEGRATION_CONNECTOR_BUNDLE_APPLY",
        description: "Применение bundle интеграции (после preview)",
        parameterTemplate: {
            replaceExisting: false,
            bundle: {
                httpProcessingProfile: {
                    enabled: true,
                    addDirectionHeader: true,
                    directionHeaderName: "X-Integration-Direction",
                    requestEnvelopeEnabled: true,
                    parseJsonBody: true,
                    responseBodyMaxChars: 2000
                },
                connectorPresets: {
                    messageBrokers: [
                        {id: "webhook-bus", type: "WEBHOOK_HTTP", properties: {url: "https://gateway.customer.local/events"}}
                    ],
                    externalRestServices: [
                        {id: "crm", baseUrl: "https://crm.customer.local"}
                    ]
                }
            }
        }
    }
];
const STUDIO_OPS_CACHE_KEY = "integration-ui-studio-operations-catalog";

function normalizeStudioOperationsCatalog(rawCatalog) {
    if (!Array.isArray(rawCatalog)) {
        return [];
    }
    return rawCatalog
        .filter(item => item && typeof item.operation === "string" && item.operation.trim().length > 0)
        .map(item => ({
            operation: item.operation.trim().toUpperCase(),
            description: typeof item.description === "string" ? item.description : "",
            parameterTemplate: item.parameterTemplate && typeof item.parameterTemplate === "object"
                ? item.parameterTemplate
                : {}
        }));
}

function readStudioOperationsCatalogCache() {
    try {
        const raw = localStorage.getItem(STUDIO_OPS_CACHE_KEY);
        if (!raw) {
            return [];
        }
        return normalizeStudioOperationsCatalog(JSON.parse(raw));
    } catch (error) {
        return [];
    }
}

const state = {
    apiKey: localStorage.getItem("integration-ui-api-key") || "",
    scripts: [],
    languages: [],
    translations: {},
    language: localStorage.getItem("integration-ui-lang") || "ru",
    anonymousMode: localStorage.getItem("integration-ui-anonymous-mode") === "true",
    importPreview: null,
    scriptEditorFontSize: Number(localStorage.getItem("integration-ui-editor-font-size") || "13"),
    debugPresets: JSON.parse(localStorage.getItem("integration-ui-debug-presets") || "[]"),
    brokerTypes: [],
    studioOperationsCatalog: readStudioOperationsCatalogCache(),
    activeTab: localStorage.getItem("integration-ui-active-tab") || "eventing",
    dashboardRaw: {
        dlq: [],
        outbox: [],
        inbox: []
    }
};

const el = (id) => document.getElementById(id);
const PARAM_RE = /\{\{\s*([a-zA-Z0-9_.-]+)\s*}}/g;

function setStatus(message) {
    el("uiStatus").textContent = message;
}

function headers(extra = {}) {
    if (state.anonymousMode) {
        return {...extra};
    }
    return state.apiKey
        ? {"X-API-KEY": state.apiKey, ...extra}
        : {...extra};
}

async function apiGet(url) {
    const resp = await fetch(url, {headers: headers({"Content-Type": "application/json"})});
    if (!resp.ok) throw new Error(`${url}: HTTP ${resp.status}`);
    return resp.json();
}

async function apiPost(url, body) {
    const resp = await fetch(url, {method: "POST", headers: headers({"Content-Type": "application/json"}), body: JSON.stringify(body ?? {})});
    if (!resp.ok) throw new Error(`${url}: HTTP ${resp.status}`);
    return resp.json();
}

async function apiPostMultipart(url, formData) {
    const resp = await fetch(url, {method: "POST", headers: headers(), body: formData});
    if (!resp.ok) {
        const text = await resp.text();
        throw new Error(`${url}: HTTP ${resp.status} ${text}`);
    }
    return resp.json();
}

async function apiPut(url, body) {
    const resp = await fetch(url, {method: "PUT", headers: headers({"Content-Type": "application/json"}), body: JSON.stringify(body ?? {})});
    if (!resp.ok) throw new Error(`${url}: HTTP ${resp.status}`);
    return resp.json();
}

async function apiDelete(url) {
    const resp = await fetch(url, {method: "DELETE", headers: headers({"Content-Type": "application/json"})});
    if (!resp.ok) throw new Error(`${url}: HTTP ${resp.status}`);
    return resp.json();
}

async function loadI18n() {
    state.languages = await fetch("/ui/i18n/languages.json").then(r => r.json());
    for (const lang of state.languages) {
        state.translations[lang.code] = await fetch(`/ui/i18n/${lang.code}.json`).then(r => r.json());
    }
    const select = el("languageSelect");
    select.innerHTML = state.languages.map(l => `<option value="${l.code}">${l.label}</option>`).join("");
    if (!state.translations[state.language]) state.language = "ru";
    select.value = state.language;
    applyI18n();
}

function t(key) {
    return (state.translations[state.language] || {})[key] || key;
}

function applyI18n() {
    document.documentElement.lang = state.language;
    document.querySelectorAll("[data-i18n]").forEach(node => {
        node.textContent = t(node.getAttribute("data-i18n"));
    });
}

function setupTabs() {
    const buttons = [...document.querySelectorAll(".tab-btn")];
    const panels = [...document.querySelectorAll("[data-tab-panel]")];
    const openTab = (tabId) => {
        if (!buttons.some(btn => btn.dataset.tabTarget === tabId)) {
            tabId = "eventing";
        }
        state.activeTab = tabId;
        localStorage.setItem("integration-ui-active-tab", tabId);
        buttons.forEach(btn => btn.classList.toggle("active", btn.dataset.tabTarget === tabId));
        panels.forEach(panel => panel.classList.toggle("hidden", panel.dataset.tabPanel !== tabId));
    };
    buttons.forEach(button => {
        button.addEventListener("click", () => openTab(button.dataset.tabTarget));
    });
    openTab(state.activeTab);
}

function setupEditorExperience() {
    const editor = el("scriptBodyInput");
    editor.style.fontSize = `${state.scriptEditorFontSize}px`;
    editor.style.whiteSpace = el("wordWrapInput").checked ? "pre-wrap" : "pre";

    editor.addEventListener("keydown", (event) => {
        if (event.key === "Tab") {
            event.preventDefault();
            const start = editor.selectionStart;
            const end = editor.selectionEnd;
            editor.value = `${editor.value.substring(0, start)}    ${editor.value.substring(end)}`;
            editor.selectionStart = editor.selectionEnd = start + 4;
            renderExecutionParamsFromScript();
            return;
        }
        if (event.key === "s" && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            saveScript().catch(e => setStatus(`Ошибка save: ${e.message}`));
            return;
        }
        if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
            event.preventDefault();
            if (event.shiftKey) {
                executeScriptAdvanced().catch(e => setStatus(`Ошибка execute: ${e.message}`));
            } else {
                debugScript().catch(e => setStatus(`Ошибка debug: ${e.message}`));
            }
        }
    });
    editor.addEventListener("keyup", updateEditorCursorInfo);
    editor.addEventListener("click", updateEditorCursorInfo);
    updateEditorCursorInfo();
}

function updateEditorCursorInfo() {
    const editor = el("scriptBodyInput");
    const pos = editor.selectionStart ?? 0;
    const linesBefore = editor.value.substring(0, pos).split("\n");
    const line = linesBefore.length;
    const col = linesBefore[linesBefore.length - 1].length + 1;
    el("editorCursorInfo").textContent = `Ln ${line}, Col ${col}`;
}

function setEditorFontSize(delta) {
    state.scriptEditorFontSize = Math.max(11, Math.min(24, state.scriptEditorFontSize + delta));
    localStorage.setItem("integration-ui-editor-font-size", String(state.scriptEditorFontSize));
    el("scriptBodyInput").style.fontSize = `${state.scriptEditorFontSize}px`;
}

function formatJsonTextarea(elementId, label) {
    const value = parseJsonInput(elementId, label);
    el(elementId).value = JSON.stringify(value, null, 2);
}

function formatScriptBody() {
    const raw = el("scriptBodyInput").value || "";
    const lines = raw.split("\n").map(line => line.replace(/\s+$/g, ""));
    el("scriptBodyInput").value = lines.join("\n");
    updateEditorCursorInfo();
    renderExecutionParamsFromScript();
}

function paintStats(stats) {
    const cards = [
        ["processed", stats.processedCount],
        ["duplicates", stats.duplicateCount],
        ["dlq", stats.dlqSize],
        ["outbox pending", stats.outboxPendingSize],
        ["outbox failed", stats.outboxFailedSize],
        ["outbox dead", stats.outboxDeadSize],
        ["inbox in-progress", stats.inboxInProgressSize]
    ];
    el("statsCards").innerHTML = cards.map(([title, value]) =>
        `<div class="card"><div class="title">${title}</div><div class="value">${value ?? 0}</div></div>`).join("");
}

function applyEventSearchFilter() {
    const query = (el("eventSearchInput")?.value || "").trim().toLowerCase();
    const filter = (items) => {
        if (!query) {
            return items;
        }
        return (items || []).filter(item => JSON.stringify(item).toLowerCase().includes(query));
    };
    el("dlqView").textContent = JSON.stringify(filter(state.dashboardRaw.dlq), null, 2);
    el("outboxView").textContent = JSON.stringify(filter(state.dashboardRaw.outbox), null, 2);
    el("inboxView").textContent = JSON.stringify(filter(state.dashboardRaw.inbox), null, 2);
}

async function refreshDashboard() {
    const outboxStatus = encodeURIComponent(el("outboxStatusFilterInput")?.value || "");
    const includeSent = el("includeSentOutboxInput")?.checked ? "true" : "false";
    const [stats, dlq, outbox, inbox] = await Promise.all([
        apiGet("/api/v1/events/stats"),
        apiGet("/api/v1/events/dlq?limit=20"),
        apiGet(`/api/v1/events/outbox?limit=20&status=${outboxStatus}&includeSent=${includeSent}`),
        apiGet("/api/v1/events/inbox?limit=20")
    ]);
    paintStats(stats);
    state.dashboardRaw = {dlq, outbox, inbox};
    applyEventSearchFilter();
}

function parseJsonInput(elementId, label) {
    const raw = (el(elementId).value || "").trim();
    if (!raw) {
        return {};
    }
    try {
        return JSON.parse(raw);
    } catch (e) {
        throw new Error(`${label}: некорректный JSON (${e.message})`);
    }
}

function fillScriptForm(script) {
    el("scriptIdInput").value = script.scriptId || "";
    el("scriptTypeInput").value = script.type || "BRANCH_CACHE_QUERY";
    el("scriptDescriptionInput").value = script.description || "";
    el("scriptBodyInput").value = script.scriptBody || "";
    renderExecutionParamsFromScript();
}

function renderScriptsChecklist() {
    el("scriptsChecklist").innerHTML = state.scripts.map(s =>
        `<label class="checkbox-row"><input type="checkbox" data-script-id="${s.scriptId}"/> ${s.scriptId} <span class="script-type">(${s.type})</span></label>`).join("");
}

const SCRIPT_SNIPPETS = {
    branchStatus: `def branchId = payload.branchId ?: params.branchId
if (!branchId) {
    return [ok: false, reason: "branchId is required"]
}
def nextState = payload.state ?: "UNKNOWN"
return [
    ok: true,
    eventType: "VISIT_BRANCH_STATE_UPDATED",
    meta: [source: context.source ?: "ui-editor"],
    data: [branchId: branchId, state: nextState]
]`,
    busAck: `def eventId = payload.eventId ?: java.util.UUID.randomUUID().toString()
return [
    ack: true,
    eventId: eventId,
    topic: context.topic ?: "customer.branch.events",
    processedAt: java.time.Instant.now().toString()
]`,
    restRoute: `def customerId = payload.customerId ?: params.customerId
if (!customerId) throw new IllegalArgumentException("customerId required")
return [
    operation: "REST_INVOKE",
    serviceId: params.serviceId ?: "customer-crm",
    method: "POST",
    path: "/api/v1/customers/${customerId}/sync",
    body: [customerId: customerId, snapshot: payload]
]`
};

function insertSnippet() {
    const key = el("snippetSelect").value;
    if (!key || !SCRIPT_SNIPPETS[key]) {
        return;
    }
    const editor = el("scriptBodyInput");
    const snippet = SCRIPT_SNIPPETS[key];
    const prefix = editor.value.trim().length === 0 ? "" : "\n\n";
    editor.value = `${editor.value}${prefix}${snippet}`.trimStart();
    renderExecutionParamsFromScript();
    updateEditorCursorInfo();
    setStatus(`Snippet inserted: ${key}`);
}

function renderDebugPresets() {
    const select = el("debugPresetSelect");
    select.innerHTML = [`<option value="">${t("chooseDebugPreset")}</option>`]
        .concat(state.debugPresets.map(item => `<option value="${item.id}">${item.id}</option>`))
        .join("");
}

function renderDebugHistoryOptions(entries) {
    const select = el("debugHistorySelect");
    const options = [`<option value="">${t("chooseDebugHistory")}</option>`]
        .concat(entries.map((item, idx) => `<option value="${idx}">${item.scriptId} | ${item.startedAt} | ${item.ok ? "OK" : "ERR"}</option>`));
    select.innerHTML = options.join("");
}

function saveDebugPreset() {
    const scriptId = el("scriptIdInput").value.trim();
    if (!scriptId) {
        throw new Error("Для сохранения preset требуется scriptId");
    }
    const preset = {
        id: `${scriptId}-${Date.now()}`,
        scriptId,
        payload: parseJsonInput("debugPayloadInput", "Payload"),
        context: parseJsonInput("debugContextInput", "Context"),
        parameters: collectExecutionParameterValues()
    };
    state.debugPresets = [preset, ...state.debugPresets].slice(0, 20);
    localStorage.setItem("integration-ui-debug-presets", JSON.stringify(state.debugPresets));
    renderDebugPresets();
    el("debugPresetSelect").value = preset.id;
    setStatus(`Debug preset сохранен: ${preset.id}`);
}

function loadDebugPreset() {
    const presetId = el("debugPresetSelect").value;
    const preset = state.debugPresets.find(item => item.id === presetId);
    if (!preset) {
        throw new Error("Preset не найден");
    }
    el("scriptIdInput").value = preset.scriptId;
    el("debugPayloadInput").value = JSON.stringify(preset.payload || {}, null, 2);
    el("debugContextInput").value = JSON.stringify(preset.context || {}, null, 2);
    const values = preset.parameters || {};
    el("executionParamsGrid").innerHTML = Object.entries(values).map(([name, value]) => `
      <label>${name}
        <input data-exec-param="${name}" value="${value ?? ""}" />
      </label>
    `).join("");
    setStatus(`Debug preset загружен: ${presetId}`);
}

function setAllScriptsChecked(checked) {
    el("scriptsChecklist").querySelectorAll("input[data-script-id]").forEach(input => {
        input.checked = checked;
    });
}

async function loadDebugHistory() {
    const scriptId = el("scriptIdInput").value.trim();
    const query = scriptId ? `?scriptId=${encodeURIComponent(scriptId)}&limit=50` : "?limit=50";
    const entries = await apiGet(`/api/v1/program/scripts/debug/history${query}`);
    renderDebugHistoryOptions(entries);
    el("debugHistoryView").textContent = JSON.stringify(entries, null, 2);
}

async function clearDebugHistory() {
    const scriptId = el("scriptIdInput").value.trim();
    const query = scriptId ? `?scriptId=${encodeURIComponent(scriptId)}` : "";
    const result = await apiDelete(`/api/v1/program/scripts/debug/history${query}`);
    el("debugHistorySelect").innerHTML = `<option value="">${t("chooseDebugHistory")}</option>`;
    el("debugHistoryView").textContent = JSON.stringify(result, null, 2);
    setStatus(`Debug history очищена: ${result.removed ?? 0}`);
}

async function replayLastDebug() {
    const scriptId = el("scriptIdInput").value.trim();
    if (!scriptId) {
        throw new Error("Укажите scriptId для replay last debug");
    }
    const result = await apiPost(`/api/v1/program/scripts/${encodeURIComponent(scriptId)}/debug/replay-last`, {});
    el("debugResultView").textContent = JSON.stringify(result, null, 2);
    await loadDebugHistory();
}

function showSelectedDebugHistory() {
    const raw = el("debugHistoryView").textContent.trim();
    if (!raw) {
        return;
    }
    const selected = el("debugHistorySelect").value;
    if (selected === "") {
        return;
    }
    const entries = JSON.parse(raw);
    const item = entries[Number(selected)];
    if (!item) {
        return;
    }
    el("debugPayloadInput").value = JSON.stringify(item.payload || {}, null, 2);
    el("debugContextInput").value = JSON.stringify(item.context || {}, null, 2);
    const params = item.parameters || {};
    el("executionParamsGrid").innerHTML = Object.entries(params).map(([name, value]) => `
      <label>${name}
        <input data-exec-param="${name}" value="${value ?? ""}" />
      </label>
    `).join("");
}

async function loadScripts() {
    state.scripts = await apiGet("/api/v1/program/scripts");
    const select = el("scriptsSelect");
    select.innerHTML = state.scripts.map(s => `<option value="${s.scriptId}">${s.scriptId} (${s.type})</option>`).join("");
    renderScriptsChecklist();
}

async function loadSelectedScript() {
    const scriptId = el("scriptsSelect").value;
    if (!scriptId) return;
    const script = await apiGet(`/api/v1/program/scripts/${encodeURIComponent(scriptId)}`);
    fillScriptForm(script);
}

function extractScriptParams(scriptBody) {
    const keys = new Set();
    for (const match of scriptBody.matchAll(PARAM_RE)) {
        keys.add(match[1]);
    }
    return [...keys];
}

function renderExecutionParamsFromScript() {
    const currentValues = collectExecutionParameterValues();
    const keys = extractScriptParams(el("scriptBodyInput").value || "");
    const grid = el("executionParamsGrid");
    if (keys.length === 0 && Object.keys(currentValues).length === 0) {
        grid.innerHTML = `<div class="mono">Параметры не найдены. Используйте {{paramName}} в скрипте или добавьте вручную.</div>`;
        return;
    }
    const mergedKeys = [...new Set([...keys, ...Object.keys(currentValues)])];
    grid.innerHTML = mergedKeys.map(key => `
      <label>${key}
        <input data-exec-param="${key}" value="${currentValues[key] ?? ""}" />
      </label>
    `).join("");
}

function addExecutionParam() {
    const key = window.prompt("Введите имя параметра:");
    if (!key) return;
    const values = collectExecutionParameterValues();
    if (!Object.prototype.hasOwnProperty.call(values, key)) {
        values[key] = "";
    }
    const grid = el("executionParamsGrid");
    grid.innerHTML = Object.entries(values).map(([name, value]) => `
      <label>${name}
        <input data-exec-param="${name}" value="${value ?? ""}" />
      </label>
    `).join("");
}

async function saveScript() {
    const scriptId = el("scriptIdInput").value.trim();
    if (!scriptId) throw new Error("scriptId обязателен");
    await apiPut(`/api/v1/program/scripts/${encodeURIComponent(scriptId)}`, {
        type: el("scriptTypeInput").value,
        description: el("scriptDescriptionInput").value,
        scriptBody: el("scriptBodyInput").value
    });
    await loadScripts();
    el("scriptsSelect").value = scriptId;
    setStatus(`Script ${scriptId} saved`);
}

async function validateScript() {
    const payload = {
        type: el("scriptTypeInput").value,
        description: el("scriptDescriptionInput").value,
        scriptBody: el("scriptBodyInput").value
    };
    const result = await apiPost("/api/v1/program/scripts/validate", payload);
    el("debugResultView").textContent = JSON.stringify(result, null, 2);
    setStatus(result.ok ? "Script validation OK" : `Script validation error: ${result.message}`);
}

function analyzeScriptBody() {
    const body = el("scriptBodyInput").value || "";
    const stack = [];
    const pairs = new Map([["}", "{"], ["]", "["], [")", "("]]);
    let line = 1;
    const issues = [];
    for (const ch of body) {
        if (ch === "\n") {
            line++;
            continue;
        }
        if (["{", "[", "("].includes(ch)) {
            stack.push({ch, line});
            continue;
        }
        if (pairs.has(ch)) {
            const last = stack.pop();
            if (!last || last.ch !== pairs.get(ch)) {
                issues.push(`Несоответствие скобок на строке ${line}: '${ch}'`);
            }
        }
    }
    while (stack.length > 0) {
        const left = stack.pop();
        issues.push(`Незакрытая скобка '${left.ch}' на строке ${left.line}`);
    }
    const params = extractScriptParams(body);
    const report = {
        ok: issues.length === 0,
        lineCount: body.split("\n").length,
        params,
        issues
    };
    el("debugResultView").textContent = JSON.stringify(report, null, 2);
    setStatus(report.ok ? "Script analysis OK" : `Script analysis issues: ${issues.length}`);
}

async function debugScript() {
    const scriptId = el("scriptIdInput").value.trim();
    const payloadText = el("debugPayloadInput").value.trim() || "{}";
    const payload = JSON.parse(payloadText);
    const result = await apiPost(`/api/v1/program/scripts/${encodeURIComponent(scriptId)}/debug-advanced`, {
        payload,
        parameters: collectExecutionParameterValues(),
        context: parseJsonInput("debugContextInput", "Debug context")
    });
    el("debugResultView").textContent = JSON.stringify(result, null, 2);
}

async function executeScriptAdvanced() {
    const scriptId = el("scriptIdInput").value.trim();
    const payload = parseJsonInput("debugPayloadInput", "Payload");
    const result = await apiPost(`/api/v1/program/scripts/${encodeURIComponent(scriptId)}/execute-advanced`, {
        payload,
        parameters: collectExecutionParameterValues(),
        context: parseJsonInput("debugContextInput", "Execute context")
    });
    el("debugResultView").textContent = JSON.stringify(result, null, 2);
}

function collectExecutionParameterValues() {
    const values = {};
    const grid = el("executionParamsGrid");
    if (!grid) {
        return values;
    }
    grid.querySelectorAll("input[data-exec-param]").forEach(input => {
        values[input.dataset.execParam] = input.value;
    });
    return values;
}

function renderTemplateParameters(preview) {
    const container = el("templateParametersGrid");
    if (!preview || !preview.parameters || preview.parameters.length === 0) {
        container.innerHTML = `<div class="mono">${t("parameters")}: []</div>`;
        return;
    }
    container.innerHTML = `<h4>${t("parameters")}</h4>` + preview.parameters.map(p => `
      <label>${p.label || p.key} (${p.key})
        <input data-param-key="${p.key}" value="${p.defaultValue || ""}" placeholder="${p.description || ""}" />
      </label>
    `).join("") + `<h4>${t("scriptsInTemplate")}</h4><pre class="mono">${JSON.stringify(preview.scripts, null, 2)}</pre>`;
}

function selectedTemplateFile() {
    const file = el("templateFileInput").files[0];
    if (!file) throw new Error("ITS-файл не выбран");
    return file;
}

async function previewTemplate() {
    const form = new FormData();
    form.append("archive", selectedTemplateFile());
    const preview = await apiPostMultipart("/api/v1/program/templates/import/preview", form);
    state.importPreview = preview;
    el("templateMetaView").textContent = JSON.stringify(preview, null, 2);
    renderTemplateParameters(preview);
    setStatus(`Template preview: ${preview.templateId}`);
}

async function validateTemplateArchive() {
    const form = new FormData();
    form.append("archive", selectedTemplateFile());
    const result = await apiPostMultipart("/api/v1/program/templates/import/validate", form);
    el("templateMetaView").textContent = JSON.stringify(result, null, 2);
    setStatus(result.ok ? "Template archive validation OK" : "Template archive validation: warnings");
}

function collectImportParameterValues() {
    const values = {};
    el("templateParametersGrid").querySelectorAll("input[data-param-key]").forEach(input => {
        values[input.dataset.paramKey] = input.value;
    });
    return values;
}

async function importTemplate() {
    const form = new FormData();
    form.append("archive", selectedTemplateFile());
    form.append("parameterValues", JSON.stringify(collectImportParameterValues()));
    form.append("replaceExisting", String(el("replaceExistingInput").checked));
    const result = await apiPostMultipart("/api/v1/program/templates/import", form);
    el("templateMetaView").textContent = JSON.stringify(result, null, 2);
    await loadScripts();
    setStatus(`Imported scripts: ${(result.importedScripts || []).join(", ")}`);
}

function selectedExportScriptIds() {
    return [...el("scriptsChecklist").querySelectorAll("input[data-script-id]:checked")]
        .map(i => i.dataset.scriptId);
}

async function exportTemplate() {
    const scriptIds = selectedExportScriptIds();
    if (scriptIds.length === 0) {
        throw new Error("Выберите минимум один скрипт для экспорта");
    }
    const payload = {
        templateId: el("exportTemplateIdInput").value.trim(),
        name: el("exportTemplateNameInput").value.trim(),
        description: el("exportTemplateDescriptionInput").value.trim(),
        scriptIds,
        parameterDefaults: JSON.parse(el("exportDefaultsInput").value.trim() || "{}")
    };
    const resp = await fetch("/api/v1/program/templates/export", {
        method: "POST",
        headers: headers({"Content-Type": "application/json"}),
        body: JSON.stringify(payload)
    });
    if (!resp.ok) throw new Error(`Export failed HTTP ${resp.status}`);
    const blob = await resp.blob();
    const fileName = `${payload.templateId || "integration-template"}.its`;
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(a.href);
    setStatus(`Exported: ${fileName}`);
}

async function replayDlqBulk() {
    const limit = Number(el("replayDlqLimitInput").value || "50");
    const result = await apiPost(`/api/v1/events/replay-dlq?limit=${encodeURIComponent(limit)}`, {});
    el("eventOpsResultView").textContent = JSON.stringify(result, null, 2);
    await refreshDashboard();
    setStatus(`Replay DLQ завершен, обработано: ${result.length ?? 0}`);
}

async function previewMaintenance() {
    const result = await apiGet("/api/v1/events/maintenance/preview");
    el("eventOpsResultView").textContent = JSON.stringify(result, null, 2);
    setStatus("Maintenance preview готов");
}

async function runMaintenance() {
    const result = await apiPost("/api/v1/events/maintenance/run", {});
    el("eventOpsResultView").textContent = JSON.stringify(result, null, 2);
    await refreshDashboard();
    setStatus("Maintenance run выполнен");
}

async function clearDlq() {
    const result = await apiDelete("/api/v1/events/dlq");
    el("eventOpsResultView").textContent = JSON.stringify(result, null, 2);
    await refreshDashboard();
    setStatus(`DLQ очищен: ${result.removed ?? 0}`);
}

async function clearInboxByStatus() {
    const status = el("inboxStatusInput").value || "";
    const result = await apiDelete(`/api/v1/events/inbox?status=${encodeURIComponent(status)}`);
    el("eventOpsResultView").textContent = JSON.stringify(result, null, 2);
    await refreshDashboard();
    setStatus(`Inbox очищен: ${result.removed ?? 0}`);
}

async function retryOutboxEventById() {
    const eventId = el("retryOutboxEventIdInput").value.trim();
    if (!eventId) {
        throw new Error("Укажите eventId для retry outbox");
    }
    const result = await apiPost(`/api/v1/events/outbox/retry/${encodeURIComponent(eventId)}`, {});
    el("eventOpsResultView").textContent = JSON.stringify(result, null, 2);
    await refreshDashboard();
    setStatus(`Outbox retry result: ${result.status}`);
}

async function exportEventSnapshot() {
    const snapshot = await apiGet("/api/v1/events/export");
    el("snapshotPayloadInput").value = JSON.stringify(snapshot, null, 2);
    el("snapshotResultView").textContent = JSON.stringify({
        processed: snapshot.processed?.length ?? 0,
        dlq: snapshot.dlq?.length ?? 0,
        outbox: snapshot.outbox?.length ?? 0
    }, null, 2);
    setStatus("Snapshot экспортирован в форму");
}

async function runSnapshotOperation(mode) {
    const payload = parseJsonInput("snapshotPayloadInput", "Snapshot payload");
    const clearBeforeImport = el("snapshotClearBeforeImportInput").checked;
    const strictPolicies = el("snapshotStrictInput").checked;
    const query = `clearBeforeImport=${clearBeforeImport}&strictPolicies=${strictPolicies}`;
    let result;
    if (mode === "validate") {
        result = await apiPost(`/api/v1/events/import/validate?strictPolicies=${strictPolicies}`, payload);
    } else if (mode === "preview") {
        result = await apiPost(`/api/v1/events/import/preview?${query}`, payload);
    } else if (mode === "analyze") {
        result = await apiPost(`/api/v1/events/import/analyze?${query}`, payload);
    } else if (mode === "import") {
        result = await apiPost(`/api/v1/events/import?clearBeforeImport=${clearBeforeImport}`, payload);
    } else {
        throw new Error(`Неизвестный режим snapshot операции: ${mode}`);
    }
    el("snapshotResultView").textContent = JSON.stringify(result, null, 2);
    if (mode === "import") {
        await refreshDashboard();
    }
    setStatus(`Snapshot операция выполнена: ${mode}`);
}

async function loadConnectorsCatalog() {
    const result = await apiGet("/api/v1/program/connectors/catalog");
    el("connectorsCatalogView").textContent = JSON.stringify(result, null, 2);
    setStatus("Каталог коннекторов загружен");
}

async function loadConnectorsHealth() {
    const result = await apiGet("/api/v1/program/connectors/health");
    el("connectorsCatalogView").textContent = JSON.stringify(result, null, 2);
    setStatus(result.ok ? "Connectors health OK" : "Connectors health has warnings");
}

async function loadSupportedBrokerTypes() {
    const result = await apiGet("/api/v1/program/connectors/broker-types");
    el("brokerTypesView").textContent = JSON.stringify(result, null, 2);
    state.brokerTypes = result.supportedBrokerTypes || [];
    setStatus("Список поддерживаемых broker types загружен");
}

function applyFirstBrokerTypeToForm() {
    if (!state.brokerTypes || state.brokerTypes.length === 0) {
        throw new Error("Сначала загрузите broker types");
    }
    const first = state.brokerTypes[0];
    el("brokerIdInput").value = `broker-${first.toLowerCase()}`;
    el("brokerTopicInput").value = "customer.events.generic";
    const headers = parseJsonInput("brokerHeadersInput", "Bus headers");
    headers["x-broker-type"] = first;
    el("brokerHeadersInput").value = JSON.stringify(headers, null, 2);
    setStatus(`В форму publish подставлен broker type: ${first}`);
}

function fillInboundTemplate() {
    el("inboundBrokerIdInput").value = el("brokerIdInput").value || "customer-databus";
    el("inboundTopicInput").value = "customer.branch.events";
    el("inboundPayloadInput").value = JSON.stringify({
        eventType: "VISIT_BRANCH_STATE_UPDATED",
        meta: {source: "ui-template"},
        data: {branchId: "BR-100", state: "OPEN"}
    }, null, 2);
    el("inboundHeadersInput").value = JSON.stringify({"x-replay": "false"}, null, 2);
    setStatus("Inbound шаблон заполнен");
}

async function invokeExternalRest() {
    const result = await apiPost("/api/v1/program/connectors/rest/invoke", {
        serviceId: el("restServiceIdInput").value.trim(),
        method: el("restMethodInput").value.trim(),
        path: el("restPathInput").value.trim(),
        headers: parseJsonInput("restHeadersInput", "REST headers"),
        body: parseJsonInput("restBodyInput", "REST body")
    });
    el("connectorsResultView").textContent = JSON.stringify(result, null, 2);
    setStatus("REST connector invoke выполнен");
}

async function publishToBus() {
    const result = await apiPost("/api/v1/program/connectors/bus/publish", {
        brokerId: el("brokerIdInput").value.trim(),
        topic: el("brokerTopicInput").value.trim(),
        key: el("brokerKeyInput").value.trim(),
        headers: parseJsonInput("brokerHeadersInput", "Bus headers"),
        payload: parseJsonInput("brokerPayloadInput", "Bus payload")
    });
    el("connectorsResultView").textContent = JSON.stringify(result, null, 2);
    setStatus("Публикация в брокер выполнена");
}

async function runInboundReaction() {
    const result = await apiPost("/api/v1/program/messages/inbound", {
        brokerId: el("inboundBrokerIdInput").value.trim(),
        topic: el("inboundTopicInput").value.trim(),
        key: el("inboundKeyInput").value.trim(),
        payload: parseJsonInput("inboundPayloadInput", "Inbound payload"),
        headers: parseJsonInput("inboundHeadersInput", "Inbound headers"),
        scriptId: el("inboundScriptIdInput").value.trim()
    });
    el("connectorsResultView").textContent = JSON.stringify(result, null, 2);
    setStatus(`Inbound reaction выполнен, результатов: ${Array.isArray(result) ? result.length : 0}`);
}

async function loadStudioBootstrap() {
    const limit = Number(el("studioHistoryLimitInput").value || "20");
    const result = await apiGet(`/api/v1/program/studio/bootstrap?debugHistoryLimit=${encodeURIComponent(limit)}`);
    el("studioBootstrapView").textContent = JSON.stringify(result, null, 2);
    const settings = result.editorSettings || {};
    el("studioThemeInput").value = settings.theme || "dark";
    el("studioFontSizeInput").value = settings.fontSize || 14;
    el("studioAutoSaveInput").checked = settings.autoSave !== false;
    el("studioWordWrapInput").checked = settings.wordWrap !== false;
    el("studioLastScriptIdInput").value = settings.lastScriptId || "";
    setStatus("Studio bootstrap загружен");
}

async function loadStudioSettings() {
    const result = await apiGet("/api/v1/program/studio/settings");
    el("studioSettingsView").textContent = JSON.stringify(result, null, 2);
    el("studioThemeInput").value = result.theme || "dark";
    el("studioFontSizeInput").value = result.fontSize || 14;
    el("studioAutoSaveInput").checked = result.autoSave !== false;
    el("studioWordWrapInput").checked = result.wordWrap !== false;
    el("studioLastScriptIdInput").value = result.lastScriptId || "";
    setStatus("Studio settings загружены");
}

async function loadStudioCapabilities() {
    const result = await apiGet("/api/v1/program/studio/capabilities");
    el("studioSettingsView").textContent = JSON.stringify(result, null, 2);
    setStatus("Studio capabilities загружены");
}

async function saveStudioSettings() {
    const payload = {
        theme: el("studioThemeInput").value,
        fontSize: Number(el("studioFontSizeInput").value || "14"),
        autoSave: el("studioAutoSaveInput").checked,
        wordWrap: el("studioWordWrapInput").checked,
        lastScriptId: el("studioLastScriptIdInput").value.trim(),
        updatedAt: null
    };
    const result = await apiPut("/api/v1/program/studio/settings", payload);
    el("studioSettingsView").textContent = JSON.stringify(result, null, 2);
    setStatus("Studio settings сохранены");
}

async function loadStudioPlaybook() {
    const result = await apiGet("/api/v1/program/studio/playbook");
    el("studioPlaybookView").textContent = JSON.stringify(result, null, 2);
    setStatus("Studio playbook загружен");
}

function renderStudioOperationCatalog() {
    const select = el("studioOperationInput");
    if (!select) {
        return;
    }
    const items = normalizeStudioOperationsCatalog(state.studioOperationsCatalog || []);
    const fallbackItems = items.length > 0 ? items : defaultStudioOperationsCatalog;
    select.innerHTML = "";
    for (const item of fallbackItems) {
        const option = document.createElement("option");
        option.value = item.operation;
        option.textContent = item.operation;
        select.appendChild(option);
    }
    state.studioOperationsCatalog = [...fallbackItems];
    applyStudioOperationTemplate();
}

function applyStudioOperationTemplate() {
    const selected = el("studioOperationInput").value;
    const item = (state.studioOperationsCatalog || []).find(entry => entry.operation === selected);
    if (!item) {
        return;
    }
    el("studioOperationParamsInput").value = JSON.stringify(item.parameterTemplate || {}, null, 2);
}

async function loadStudioOperationsCatalog() {
    let result = [];
    try {
        result = await apiGet("/api/v1/program/studio/operations/catalog");
    } catch (error) {
        setStatus(`Studio operations catalog недоступен, использован локальный fallback: ${error.message}`);
    }
    state.studioOperationsCatalog = normalizeStudioOperationsCatalog(result);
    if (state.studioOperationsCatalog.length === 0) {
        state.studioOperationsCatalog = [...defaultStudioOperationsCatalog];
    }
    localStorage.setItem(STUDIO_OPS_CACHE_KEY, JSON.stringify(state.studioOperationsCatalog));
    renderStudioOperationCatalog();
    el("studioOperationView").textContent = JSON.stringify(state.studioOperationsCatalog, null, 2);
    if (Array.isArray(result) && result.length > 0) {
        setStatus("Studio operations catalog загружен");
    }
}

async function runStudioOperation() {
    const operation = el("studioOperationInput").value;
    const parameters = parseJsonInput("studioOperationParamsInput", "Studio operation parameters");
    const result = await apiPost("/api/v1/program/studio/operations", {operation, parameters});
    el("studioOperationView").textContent = JSON.stringify(result, null, 2);
    if (operation === "REFRESH_BOOTSTRAP") {
        const snapshot = result.snapshot || {};
        el("studioBootstrapView").textContent = JSON.stringify(snapshot, null, 2);
    }
    if (operation === "FLUSH_OUTBOX" || operation === "RECOVER_STALE_INBOX") {
        await refreshDashboard();
    }
    setStatus(`Studio operation выполнена: ${operation}`);
}

function init() {
    renderStudioOperationCatalog();

    el("apiKeyInput").value = state.apiKey;
    el("anonymousModeInput").checked = state.anonymousMode;
    el("apiKeyInput").disabled = state.anonymousMode;

    el("saveApiKeyBtn").onclick = () => {
        state.apiKey = el("apiKeyInput").value.trim();
        localStorage.setItem("integration-ui-api-key", state.apiKey);
        refreshDashboard().then(loadScripts).catch(e => setStatus(`Ошибка refresh: ${e.message}`));
    };
    el("anonymousModeInput").onchange = (event) => {
        state.anonymousMode = !!event.target.checked;
        localStorage.setItem("integration-ui-anonymous-mode", String(state.anonymousMode));
        el("apiKeyInput").disabled = state.anonymousMode;
        setStatus(state.anonymousMode
            ? t("anonymousEnabledHint")
            : t("anonymousDisabledHint"));
    };
    el("languageSelect").onchange = (event) => {
        state.language = event.target.value;
        localStorage.setItem("integration-ui-lang", state.language);
        applyI18n();
    };
    el("refreshStatsBtn").onclick = () => refreshDashboard().then(() => setStatus("Stats updated")).catch(e => setStatus(`Ошибка stats: ${e.message}`));
    el("eventSearchInput").addEventListener("input", () => applyEventSearchFilter());
    el("outboxStatusFilterInput").onchange = () => refreshDashboard().catch(e => setStatus(`Ошибка outbox filter: ${e.message}`));
    el("includeSentOutboxInput").onchange = () => refreshDashboard().catch(e => setStatus(`Ошибка include sent: ${e.message}`));
    el("flushOutboxBtn").onclick = async () => {
        const limit = Number(el("flushOutboxLimitInput").value || "100");
        const result = await apiPost(`/api/v1/events/outbox/flush?limit=${encodeURIComponent(limit)}`, {});
        el("eventOpsResultView").textContent = JSON.stringify(result, null, 2);
        await refreshDashboard();
        setStatus(`Outbox flushed: ${result.length ?? 0}`);
    };
    el("retryOutboxBtn").onclick = () => retryOutboxEventById().catch(e => setStatus(`Ошибка retry outbox: ${e.message}`));
    el("recoverInboxBtn").onclick = async () => {
        const result = await apiPost("/api/v1/events/inbox/recover-stale", {});
        await refreshDashboard();
        setStatus(`Recovered stale inbox: ${result.recovered ?? 0}`);
    };
    el("clearInboxBtn").onclick = () => clearInboxByStatus().catch(e => setStatus(`Ошибка clear inbox: ${e.message}`));
    el("loadScriptBtn").onclick = () => loadSelectedScript().catch(e => setStatus(`Ошибка load: ${e.message}`));
    el("newScriptBtn").onclick = () => fillScriptForm({
        scriptId: "",
        type: "BRANCH_CACHE_QUERY",
        description: "",
        scriptBody: "return [ok: true]"
    });
    el("refreshExecutionParamsBtn").onclick = () => renderExecutionParamsFromScript();
    el("addExecutionParamBtn").onclick = () => addExecutionParam();
    el("scriptBodyInput").addEventListener("input", () => renderExecutionParamsFromScript());
    el("scriptBodyInput").addEventListener("input", () => updateEditorCursorInfo());
    el("saveScriptBtn").onclick = () => saveScript().catch(e => setStatus(`Ошибка save: ${e.message}`));
    el("validateScriptBtn").onclick = () => validateScript().catch(e => setStatus(`Ошибка validate: ${e.message}`));
    el("analyzeScriptBtn").onclick = () => {
        try {
            analyzeScriptBody();
        } catch (e) {
            setStatus(`Ошибка analyze script: ${e.message}`);
        }
    };
    el("debugScriptBtn").onclick = () => debugScript().catch(e => setStatus(`Ошибка debug: ${e.message}`));
    el("executeScriptBtn").onclick = () => executeScriptAdvanced().catch(e => setStatus(`Ошибка execute: ${e.message}`));
    el("replayDlqBtn").onclick = () => replayDlqBulk().catch(e => setStatus(`Ошибка replay DLQ: ${e.message}`));
    el("maintenancePreviewBtn").onclick = () => previewMaintenance().catch(e => setStatus(`Ошибка maintenance preview: ${e.message}`));
    el("maintenanceRunBtn").onclick = () => runMaintenance().catch(e => setStatus(`Ошибка maintenance run: ${e.message}`));
    el("clearDlqBtn").onclick = () => clearDlq().catch(e => setStatus(`Ошибка clear DLQ: ${e.message}`));
    el("snapshotExportBtn").onclick = () => exportEventSnapshot().catch(e => setStatus(`Ошибка snapshot export: ${e.message}`));
    el("snapshotValidateBtn").onclick = () => runSnapshotOperation("validate").catch(e => setStatus(`Ошибка snapshot validate: ${e.message}`));
    el("snapshotPreviewBtn").onclick = () => runSnapshotOperation("preview").catch(e => setStatus(`Ошибка snapshot preview: ${e.message}`));
    el("snapshotAnalyzeBtn").onclick = () => runSnapshotOperation("analyze").catch(e => setStatus(`Ошибка snapshot analyze: ${e.message}`));
    el("snapshotImportBtn").onclick = () => runSnapshotOperation("import").catch(e => setStatus(`Ошибка snapshot import: ${e.message}`));
    el("loadConnectorsBtn").onclick = () => loadConnectorsCatalog().catch(e => setStatus(`Ошибка connectors catalog: ${e.message}`));
    el("loadBrokerTypesBtn").onclick = () => loadSupportedBrokerTypes().catch(e => setStatus(`Ошибка broker types: ${e.message}`));
    el("connectorsHealthBtn").onclick = () => loadConnectorsHealth().catch(e => setStatus(`Ошибка connectors health: ${e.message}`));
    el("invokeRestBtn").onclick = () => invokeExternalRest().catch(e => setStatus(`Ошибка invoke REST: ${e.message}`));
    el("publishBusBtn").onclick = () => publishToBus().catch(e => setStatus(`Ошибка publish bus: ${e.message}`));
    el("runInboundReactionBtn").onclick = () => runInboundReaction().catch(e => setStatus(`Ошибка inbound reaction: ${e.message}`));
    el("loadStudioBootstrapBtn").onclick = () => loadStudioBootstrap().catch(e => setStatus(`Ошибка studio bootstrap: ${e.message}`));
    el("loadStudioSettingsBtn").onclick = () => loadStudioSettings().catch(e => setStatus(`Ошибка studio settings: ${e.message}`));
    el("loadStudioCapabilitiesBtn").onclick = () => loadStudioCapabilities().catch(e => setStatus(`Ошибка studio capabilities: ${e.message}`));
    el("saveStudioSettingsBtn").onclick = () => saveStudioSettings().catch(e => setStatus(`Ошибка save studio settings: ${e.message}`));
    el("loadStudioPlaybookBtn").onclick = () => loadStudioPlaybook().catch(e => setStatus(`Ошибка studio playbook: ${e.message}`));
    el("loadStudioOpsCatalogBtn").onclick = () => loadStudioOperationsCatalog().catch(e => setStatus(`Ошибка studio operations catalog: ${e.message}`));
    el("runStudioOperationBtn").onclick = () => runStudioOperation().catch(e => setStatus(`Ошибка studio operation: ${e.message}`));
    el("studioOperationInput").onchange = () => applyStudioOperationTemplate();
    el("formatPayloadBtn").onclick = () => {
        try {
            formatJsonTextarea("debugPayloadInput", "Payload");
            setStatus("Payload JSON отформатирован");
        } catch (e) {
            setStatus(`Ошибка format payload: ${e.message}`);
        }
    };
    el("formatContextBtn").onclick = () => {
        try {
            formatJsonTextarea("debugContextInput", "Context");
            setStatus("Context JSON отформатирован");
        } catch (e) {
            setStatus(`Ошибка format context: ${e.message}`);
        }
    };
    el("formatScriptBtn").onclick = () => {
        formatScriptBody();
        setStatus("Скрипт отформатирован");
    };
    el("fontIncreaseBtn").onclick = () => setEditorFontSize(1);
    el("fontDecreaseBtn").onclick = () => setEditorFontSize(-1);
    el("insertSnippetBtn").onclick = () => insertSnippet();
    el("saveDebugPresetBtn").onclick = () => {
        try {
            saveDebugPreset();
        } catch (e) {
            setStatus(`Ошибка save preset: ${e.message}`);
        }
    };
    el("loadDebugPresetBtn").onclick = () => {
        try {
            loadDebugPreset();
        } catch (e) {
            setStatus(`Ошибка load preset: ${e.message}`);
        }
    };
    el("loadDebugHistoryBtn").onclick = () => loadDebugHistory().catch(e => setStatus(`Ошибка debug history: ${e.message}`));
    el("clearDebugHistoryBtn").onclick = () => clearDebugHistory().catch(e => setStatus(`Ошибка clear debug history: ${e.message}`));
    el("replayLastDebugBtn").onclick = () => replayLastDebug().catch(e => setStatus(`Ошибка replay last debug: ${e.message}`));
    el("debugHistorySelect").onchange = () => {
        try {
            showSelectedDebugHistory();
        } catch (e) {
            setStatus(`Ошибка show debug history: ${e.message}`);
        }
    };
    el("wordWrapInput").onchange = () => {
        el("scriptBodyInput").style.whiteSpace = el("wordWrapInput").checked ? "pre-wrap" : "pre";
    };
    el("previewTemplateBtn").onclick = () => previewTemplate().catch(e => setStatus(`Ошибка preview: ${e.message}`));
    el("validateTemplateBtn").onclick = () => validateTemplateArchive().catch(e => setStatus(`Ошибка validate archive: ${e.message}`));
    el("importTemplateBtn").onclick = () => importTemplate().catch(e => setStatus(`Ошибка import: ${e.message}`));
    el("formatExportDefaultsBtn").onclick = () => {
        try {
            formatJsonTextarea("exportDefaultsInput", "Export defaults");
            setStatus("Export defaults JSON отформатирован");
        } catch (e) {
            setStatus(`Ошибка format export defaults: ${e.message}`);
        }
    };
    el("checkAllScriptsBtn").onclick = () => setAllScriptsChecked(true);
    el("clearAllScriptsBtn").onclick = () => setAllScriptsChecked(false);
    el("exportTemplateBtn").onclick = () => exportTemplate().catch(e => setStatus(`Ошибка export: ${e.message}`));
    el("applyFirstBrokerTypeBtn").onclick = () => {
        try {
            applyFirstBrokerTypeToForm();
        } catch (e) {
            setStatus(`Ошибка apply broker type: ${e.message}`);
        }
    };
    el("fillInboundTemplateBtn").onclick = () => fillInboundTemplate();

    loadI18n()
        .then(() => refreshDashboard())
        .then(() => loadScripts())
        .then(() => renderDebugPresets())
        .then(() => setupTabs())
        .then(() => setupEditorExperience())
        .then(() => loadStudioOperationsCatalog())
        .then(() => loadStudioSettings().catch(() => null))
        .then(() => {
            if (state.anonymousMode) {
                setStatus(t("anonymousEnabledHint"));
            }
        })
        .catch(e => setStatus(`Ошибка инициализации: ${e.message}`));
}

window.addEventListener("DOMContentLoaded", init);
