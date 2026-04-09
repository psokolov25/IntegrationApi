const state = {
    apiKey: localStorage.getItem("integration-ui-api-key") || "",
    scripts: [],
    languages: [],
    translations: {},
    language: localStorage.getItem("integration-ui-lang") || "ru",
    importPreview: null
};

const el = (id) => document.getElementById(id);
const PARAM_RE = /\{\{\s*([a-zA-Z0-9_.-]+)\s*}}/g;

function setStatus(message) {
    el("uiStatus").textContent = message;
}

function headers(extra = {}) {
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

async function refreshDashboard() {
    const [stats, dlq, outbox] = await Promise.all([
        apiGet("/api/v1/events/stats"),
        apiGet("/api/v1/events/dlq?limit=20"),
        apiGet("/api/v1/events/outbox?limit=20")
    ]);
    paintStats(stats);
    el("dlqView").textContent = JSON.stringify(dlq, null, 2);
    el("outboxView").textContent = JSON.stringify(outbox, null, 2);
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

async function debugScript() {
    const scriptId = el("scriptIdInput").value.trim();
    const payloadText = el("debugPayloadInput").value.trim() || "{}";
    const payload = JSON.parse(payloadText);
    const result = await apiPost(`/api/v1/program/scripts/${encodeURIComponent(scriptId)}/debug-advanced`, {
        payload,
        parameters: collectExecutionParameterValues(),
        context: {source: "ui-debug"}
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

function init() {
    el("apiKeyInput").value = state.apiKey;

    el("saveApiKeyBtn").onclick = () => {
        state.apiKey = el("apiKeyInput").value.trim();
        localStorage.setItem("integration-ui-api-key", state.apiKey);
        refreshDashboard().then(loadScripts).catch(e => setStatus(`Ошибка refresh: ${e.message}`));
    };
    el("languageSelect").onchange = (event) => {
        state.language = event.target.value;
        localStorage.setItem("integration-ui-lang", state.language);
        applyI18n();
    };
    el("refreshStatsBtn").onclick = () => refreshDashboard().then(() => setStatus("Stats updated")).catch(e => setStatus(`Ошибка stats: ${e.message}`));
    el("flushOutboxBtn").onclick = async () => {
        await apiPost("/api/v1/events/outbox/flush?limit=100", {});
        await refreshDashboard();
        setStatus("Outbox flushed");
    };
    el("recoverInboxBtn").onclick = async () => {
        const result = await apiPost("/api/v1/events/inbox/recover-stale", {});
        await refreshDashboard();
        setStatus(`Recovered stale inbox: ${result.recovered ?? 0}`);
    };
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
    el("saveScriptBtn").onclick = () => saveScript().catch(e => setStatus(`Ошибка save: ${e.message}`));
    el("validateScriptBtn").onclick = () => validateScript().catch(e => setStatus(`Ошибка validate: ${e.message}`));
    el("debugScriptBtn").onclick = () => debugScript().catch(e => setStatus(`Ошибка debug: ${e.message}`));
    el("previewTemplateBtn").onclick = () => previewTemplate().catch(e => setStatus(`Ошибка preview: ${e.message}`));
    el("importTemplateBtn").onclick = () => importTemplate().catch(e => setStatus(`Ошибка import: ${e.message}`));
    el("exportTemplateBtn").onclick = () => exportTemplate().catch(e => setStatus(`Ошибка export: ${e.message}`));

    loadI18n()
        .then(() => refreshDashboard())
        .then(() => loadScripts())
        .catch(e => setStatus(`Ошибка инициализации: ${e.message}`));
}

window.addEventListener("DOMContentLoaded", init);
