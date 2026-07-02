const state = {
    workspaces: [],
    selectedWorkspaceId: localStorage.getItem("aiWorkspace.selectedWorkspaceId"),
    pollTimer: null,
    busy: new Set()
};

const elements = {
    appAlert: document.querySelector("#app-alert"),
    workspaceForm: document.querySelector("#workspace-form"),
    workspaceName: document.querySelector("#workspace-name"),
    refreshWorkspaces: document.querySelector("#refresh-workspaces"),
    workspaceList: document.querySelector("#workspace-list"),
    selectedWorkspaceTitle: document.querySelector("#selected-workspace-title"),
    systemStatus: document.querySelector("#system-status"),
    ingestionForm: document.querySelector("#ingestion-form"),
    jobPanel: document.querySelector("#job-panel"),
    jobId: document.querySelector("#job-id"),
    jobStatus: document.querySelector("#job-status"),
    jobSteps: document.querySelector("#job-steps"),
    refreshMemory: document.querySelector("#refresh-memory"),
    memoryView: document.querySelector("#memory-view"),
    memoryCount: document.querySelector("#memory-count"),
    questionForm: document.querySelector("#question-form"),
    question: document.querySelector("#question"),
    answerView: document.querySelector("#answer-view")
};

const terminalJobStatuses = new Set(["COMPLETED", "PARTIALLY_FAILED", "FAILED"]);
const fileInputs = ["#document-file", "#audio-file", "#image-file", "#video-file"]
    .map((selector) => document.querySelector(selector));

function setStatus(label, mode = "") {
    elements.systemStatus.textContent = label;
    elements.systemStatus.className = `status-pill ${mode}`.trim();
}

function showAlert(message, mode = "error") {
    elements.appAlert.textContent = message;
    elements.appAlert.className = `app-alert ${mode}`.trim();
}

function clearAlert() {
    elements.appAlert.textContent = "";
    elements.appAlert.className = "app-alert hidden";
}

function setBusy(scope, busy) {
    if (busy) {
        state.busy.add(scope);
    } else {
        state.busy.delete(scope);
    }

    const workspaceBusy = state.busy.has("workspaces") || state.busy.has("create-workspace");
    const ingestionBusy = state.busy.has("ingestion");
    const memoryBusy = state.busy.has("memory");
    const questionBusy = state.busy.has("question");

    elements.workspaceForm.querySelector("button").disabled = state.busy.has("create-workspace");
    elements.refreshWorkspaces.disabled = workspaceBusy;
    elements.ingestionForm.querySelector("button").disabled = ingestionBusy;
    elements.refreshMemory.disabled = memoryBusy || !state.selectedWorkspaceId;
    elements.questionForm.querySelector("button").disabled = questionBusy || !state.selectedWorkspaceId;

    elements.workspaceName.disabled = state.busy.has("create-workspace");
    elements.question.disabled = questionBusy || !state.selectedWorkspaceId;
    for (const input of fileInputs) {
        input.disabled = ingestionBusy || !state.selectedWorkspaceId;
    }
}

async function request(path, options = {}) {
    let response;
    try {
        response = await fetch(path, options);
    } catch (error) {
        throw new Error("Backend is not reachable. Check that the API server is running.");
    }

    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json")
        ? await response.json()
        : await response.text();

    if (!response.ok) {
        const message = typeof body === "object"
            ? body.detail || body.message || body.error
            : body;
        throw new Error(message || `${response.status} ${response.statusText}` || "Request failed");
    }

    return body;
}

function selectedWorkspace() {
    return state.workspaces.find((workspace) => workspace.id === state.selectedWorkspaceId) || null;
}

function requireWorkspace() {
    if (!state.selectedWorkspaceId) {
        throw new Error("Create or select a workspace first.");
    }
}

function formatDate(value) {
    if (!value) {
        return "";
    }

    return new Intl.DateTimeFormat(undefined, {
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(value));
}

function renderWorkspaces() {
    elements.workspaceList.replaceChildren();

    if (state.workspaces.length === 0) {
        const empty = document.createElement("div");
        empty.className = "empty-state";
        empty.textContent = "No workspaces";
        elements.workspaceList.append(empty);
        return;
    }

    const template = document.querySelector("#workspace-item-template");
    for (const workspace of state.workspaces) {
        const item = template.content.firstElementChild.cloneNode(true);
        item.dataset.workspaceId = workspace.id;
        item.classList.toggle("active", workspace.id === state.selectedWorkspaceId);
        item.querySelector(".workspace-name").textContent = workspace.name;
        item.querySelector(".workspace-date").textContent = workspace.id;
        item.addEventListener("click", () => selectWorkspace(workspace.id));
        elements.workspaceList.append(item);
    }
}

function renderSelectedWorkspace() {
    const workspace = selectedWorkspace();
    elements.selectedWorkspaceTitle.textContent = workspace ? workspace.name : "No workspace selected";
    setBusy("selection", false);
}

async function loadWorkspaces() {
    try {
        clearAlert();
        setBusy("workspaces", true);
        setStatus("Loading", "busy");
        state.workspaces = await request("/api/v1/workspaces");

        if (state.selectedWorkspaceId && !selectedWorkspace()) {
            state.selectedWorkspaceId = null;
            localStorage.removeItem("aiWorkspace.selectedWorkspaceId");
            clearAnswer("No answer yet");
            renderMemoryEmpty("Select a workspace", "No items loaded");
        }

        renderWorkspaces();
        renderSelectedWorkspace();
        setStatus("Ready", "ok");
        return true;
    } catch (error) {
        showError(error, "Unable to load workspaces");
        return false;
    } finally {
        setBusy("workspaces", false);
    }
}

async function createWorkspace(event) {
    event.preventDefault();
    const name = elements.workspaceName.value.trim();
    if (!name) {
        showError(new Error("Workspace name is required."));
        return;
    }

    try {
        clearAlert();
        setBusy("create-workspace", true);
        setStatus("Creating", "busy");
        const workspace = await request("/api/v1/workspaces", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({name})
        });

        elements.workspaceName.value = "";
        state.selectedWorkspaceId = workspace.id;
        localStorage.setItem("aiWorkspace.selectedWorkspaceId", workspace.id);
        await loadWorkspaces();
        await refreshMemory();
    } catch (error) {
        showError(error, "Unable to create workspace");
    } finally {
        setBusy("create-workspace", false);
    }
}

async function selectWorkspace(workspaceId) {
    state.selectedWorkspaceId = workspaceId;
    localStorage.setItem("aiWorkspace.selectedWorkspaceId", workspaceId);
    renderWorkspaces();
    renderSelectedWorkspace();
    clearAlert();
    clearAnswer("No answer yet");
    renderMemoryEmpty("Loading workspace memory", "Loading");
    await refreshMemory();
}

function appendFile(formData, key, selector) {
    const input = document.querySelector(selector);
    if (input.files.length > 0) {
        formData.append(key, input.files[0]);
    }
}

async function submitIngestion(event) {
    event.preventDefault();

    try {
        clearAlert();
        requireWorkspace();
        const formData = new FormData();
        formData.append("workspaceId", state.selectedWorkspaceId);
        appendFile(formData, "document", "#document-file");
        appendFile(formData, "audio", "#audio-file");
        appendFile(formData, "image", "#image-file");
        appendFile(formData, "video", "#video-file");

        if (!["document", "audio", "image", "video"].some((key) => formData.has(key))) {
            throw new Error("Select at least one file to upload.");
        }

        setBusy("ingestion", true);
        setStatus("Uploading", "busy");
        const submission = await request("/api/v1/orchestrator/ingestions", {
            method: "POST",
            body: formData
        });

        renderJob({
            jobId: submission.jobId,
            status: submission.status,
            steps: [
                ...submission.submitted.map((type) => ({type, status: "PENDING"})),
                ...submission.skipped.map((type) => ({type, status: "SKIPPED"}))
            ]
        });
        setStatus("RUNNING", "busy");
        pollJob(submission.jobId);
    } catch (error) {
        setBusy("ingestion", false);
        showError(error, "Upload failed");
    }
}

function renderJob(job) {
    elements.jobPanel.classList.remove("hidden");
    elements.jobId.textContent = job.jobId;
    elements.jobStatus.textContent = job.status;
    elements.jobSteps.replaceChildren();

    for (const step of job.steps || []) {
        const item = document.createElement("div");
        item.className = "step";
        const type = document.createElement("span");
        type.textContent = step.type;
        const status = document.createElement("small");
        status.textContent = step.errorMessage ? `${step.status}: ${step.errorMessage}` : step.status;
        item.append(type, status);
        elements.jobSteps.append(item);
    }
}

function pollJob(jobId) {
    if (state.pollTimer) {
        clearInterval(state.pollTimer);
    }

    const refreshJob = async () => {
        try {
            const job = await request(`/api/v1/orchestrator/jobs/${jobId}`);
            renderJob(job);
            setStatus(job.status, terminalJobStatuses.has(job.status) ? "ok" : "busy");

            if (terminalJobStatuses.has(job.status)) {
                clearInterval(state.pollTimer);
                state.pollTimer = null;
                setBusy("ingestion", false);
                await refreshMemory();
            }
        } catch (error) {
            clearInterval(state.pollTimer);
            state.pollTimer = null;
            setBusy("ingestion", false);
            showError(error, "Unable to refresh job status");
        }
    };

    refreshJob();
    state.pollTimer = setInterval(refreshJob, 1800);
}

async function refreshMemory() {
    try {
        clearAlert();
        requireWorkspace();
        setBusy("memory", true);
        setStatus("Loading", "busy");
        const memory = await request(`/api/v1/knowledge/workspaces/${state.selectedWorkspaceId}`);
        renderMemory(memory);
        setStatus("Ready", "ok");
    } catch (error) {
        if (error.message.includes("not found") || error.message.includes("Not Found")) {
            renderMemoryEmpty("No workspace memory", "No items loaded");
            setStatus("Ready", "ok");
            return;
        }
        showError(error, "Unable to load workspace memory");
    } finally {
        setBusy("memory", false);
    }
}

function renderMemoryEmpty(message, countLabel) {
    elements.memoryView.className = "memory-view empty-state";
    elements.memoryView.textContent = message;
    elements.memoryCount.textContent = countLabel;
}

function renderMemory(memory) {
    const sections = [
        ["Documents", memory.documentsInfo],
        ["Audio", memory.audioInfo],
        ["Images", memory.imagesInfo],
        ["Video", memory.videoInfo]
    ].filter(([, value]) => value && value.trim());

    elements.memoryView.className = "memory-view";
    elements.memoryView.replaceChildren();
    elements.memoryCount.textContent = `${sections.length} sections`;

    if (sections.length === 0) {
        renderMemoryEmpty("No workspace memory", "No items loaded");
        return;
    }

    for (const [title, value] of sections) {
        const block = document.createElement("article");
        block.className = "memory-block";
        const heading = document.createElement("strong");
        heading.textContent = title;
        const content = document.createElement("pre");
        content.textContent = value;
        block.append(heading, content);
        elements.memoryView.append(block);
    }
}

async function askWorkspace(event) {
    event.preventDefault();

    try {
        clearAlert();
        requireWorkspace();
        const question = elements.question.value.trim();
        if (!question) {
            showError(new Error("Question is required."));
            return;
        }

        setBusy("question", true);
        setStatus("Asking", "busy");
        elements.answerView.className = "answer-view empty-state";
        elements.answerView.textContent = "Asking workspace";
        const answer = await request(`/api/v1/knowledge/workspaces/${state.selectedWorkspaceId}/answers`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({question})
        });

        renderAnswer(answer);
        setStatus("Ready", "ok");
    } catch (error) {
        showError(error, "Unable to answer question");
        clearAnswer(error.message);
    } finally {
        setBusy("question", false);
    }
}

function renderAnswer(answer) {
    elements.answerView.className = "answer-view answer-result";
    elements.answerView.replaceChildren();

    const body = document.createElement("div");
    body.className = "answer-body";
    body.textContent = answer.answer;
    elements.answerView.append(body);

    if (!answer.sources || answer.sources.length === 0) {
        return;
    }

    const list = document.createElement("div");
    list.className = "source-list";
    for (const source of answer.sources) {
        const item = document.createElement("article");
        item.className = "source-item";

        const meta = document.createElement("div");
        meta.className = "source-meta";
        meta.textContent = [source.type, source.sourceName, source.jobId].filter(Boolean).join(" / ");

        const snippet = document.createElement("div");
        snippet.className = "source-snippet";
        snippet.textContent = source.snippet || "";

        item.append(meta, snippet);
        list.append(item);
    }

    elements.answerView.append(list);
}

function clearAnswer(message = "No answer yet") {
    elements.answerView.className = "answer-view empty-state";
    elements.answerView.textContent = message;
}

function showError(error, prefix = null) {
    const message = prefix ? `${prefix}: ${error.message}` : error.message;
    setStatus("Error", "error");
    showAlert(message);
}

elements.workspaceForm.addEventListener("submit", createWorkspace);
elements.refreshWorkspaces.addEventListener("click", () => loadWorkspaces().catch(showError));
elements.ingestionForm.addEventListener("submit", submitIngestion);
elements.refreshMemory.addEventListener("click", () => refreshMemory().catch(showError));
elements.questionForm.addEventListener("submit", askWorkspace);

async function initialize() {
    const loaded = await loadWorkspaces();
    if (!loaded) {
        return;
    }

    if (state.selectedWorkspaceId && selectedWorkspace()) {
        await refreshMemory();
        return;
    }

    renderMemoryEmpty("Select a workspace", "No items loaded");
    setStatus("Ready", "ok");
}

initialize();
