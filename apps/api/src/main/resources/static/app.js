const state = {
    workspaces: [],
    selectedWorkspaceId: localStorage.getItem("aiWorkspace.selectedWorkspaceId"),
    pollTimer: null
};

const elements = {
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

function setStatus(label, mode = "") {
    elements.systemStatus.textContent = label;
    elements.systemStatus.className = `status-pill ${mode}`.trim();
}

async function request(path, options = {}) {
    const response = await fetch(path, options);
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json")
        ? await response.json()
        : await response.text();

    if (!response.ok) {
        const message = typeof body === "object" && body.detail ? body.detail : response.statusText;
        throw new Error(message || "Request failed");
    }

    return body;
}

function selectedWorkspace() {
    return state.workspaces.find((workspace) => workspace.id === state.selectedWorkspaceId) || null;
}

function requireWorkspace() {
    if (!state.selectedWorkspaceId) {
        throw new Error("Select a workspace");
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
}

async function loadWorkspaces() {
    setStatus("Loading", "busy");
    state.workspaces = await request("/api/v1/workspaces");

    if (state.selectedWorkspaceId && !selectedWorkspace()) {
        state.selectedWorkspaceId = null;
        localStorage.removeItem("aiWorkspace.selectedWorkspaceId");
    }

    renderWorkspaces();
    renderSelectedWorkspace();
    setStatus("Ready", "ok");
}

async function createWorkspace(event) {
    event.preventDefault();
    const name = elements.workspaceName.value.trim();
    if (!name) {
        setStatus("Name required", "error");
        return;
    }

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
}

async function selectWorkspace(workspaceId) {
    state.selectedWorkspaceId = workspaceId;
    localStorage.setItem("aiWorkspace.selectedWorkspaceId", workspaceId);
    renderWorkspaces();
    renderSelectedWorkspace();
    clearAnswer();
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
        requireWorkspace();
        const formData = new FormData();
        formData.append("workspaceId", state.selectedWorkspaceId);
        appendFile(formData, "document", "#document-file");
        appendFile(formData, "audio", "#audio-file");
        appendFile(formData, "image", "#image-file");
        appendFile(formData, "video", "#video-file");

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
        pollJob(submission.jobId);
    } catch (error) {
        showError(error);
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

    state.pollTimer = setInterval(async () => {
        try {
            const job = await request(`/api/v1/orchestrator/jobs/${jobId}`);
            renderJob(job);
            setStatus(job.status, terminalJobStatuses.has(job.status) ? "ok" : "busy");

            if (terminalJobStatuses.has(job.status)) {
                clearInterval(state.pollTimer);
                state.pollTimer = null;
                await refreshMemory();
            }
        } catch (error) {
            clearInterval(state.pollTimer);
            state.pollTimer = null;
            showError(error);
        }
    }, 1800);
}

async function refreshMemory() {
    try {
        requireWorkspace();
        setStatus("Loading", "busy");
        const memory = await request(`/api/v1/knowledge/workspaces/${state.selectedWorkspaceId}`);
        renderMemory(memory);
        setStatus("Ready", "ok");
    } catch (error) {
        if (error.message.includes("not found") || error.message.includes("Not Found")) {
            elements.memoryView.className = "memory-view empty-state";
            elements.memoryView.textContent = "No workspace memory";
            elements.memoryCount.textContent = "No items loaded";
            setStatus("Ready", "ok");
            return;
        }
        showError(error);
    }
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
        elements.memoryView.className = "memory-view empty-state";
        elements.memoryView.textContent = "No workspace memory";
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
        requireWorkspace();
        const question = elements.question.value.trim();
        if (!question) {
            setStatus("Question required", "error");
            return;
        }

        setStatus("Asking", "busy");
        const answer = await request(`/api/v1/knowledge/workspaces/${state.selectedWorkspaceId}/answers`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({question})
        });

        renderAnswer(answer);
        setStatus("Ready", "ok");
    } catch (error) {
        showError(error);
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

function clearAnswer() {
    elements.answerView.className = "answer-view empty-state";
    elements.answerView.textContent = "No answer yet";
}

function showError(error) {
    setStatus("Error", "error");
    elements.answerView.className = "answer-view empty-state";
    elements.answerView.textContent = error.message;
}

elements.workspaceForm.addEventListener("submit", createWorkspace);
elements.refreshWorkspaces.addEventListener("click", () => loadWorkspaces().catch(showError));
elements.ingestionForm.addEventListener("submit", submitIngestion);
elements.refreshMemory.addEventListener("click", () => refreshMemory().catch(showError));
elements.questionForm.addEventListener("submit", askWorkspace);

loadWorkspaces()
    .then(() => {
        if (state.selectedWorkspaceId) {
            return refreshMemory();
        }
        setStatus("Ready", "ok");
        return null;
    })
    .catch(showError);
