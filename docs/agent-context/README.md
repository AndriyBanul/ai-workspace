# Agent Context

This directory contains long-term project memory for AI agents working on this repository.

Use it together with the root `AGENTS.md` file.

## How To Use This Context

Before making changes, read:

1. `AGENTS.md`
2. `DEVELOPER_CODE_PREFERENCES.md`
3. `docs/agent-context/README.md`
4. The specific files in this directory that are relevant to the task.

`DEVELOPER_CODE_PREFERENCES.md` has higher priority than the general files in this directory. If it conflicts with this directory, follow `DEVELOPER_CODE_PREFERENCES.md` unless Andrii explicitly says otherwise.

Act as a senior engineer working on a long-term commercial AI Platform product, not as a coding assistant generating isolated snippets.

Make maintainable, production-oriented decisions.

## Current Application Structure

The application code is a Gradle multi-module build rooted at `apps`.

Open `apps` in IntelliJ IDEA when working on application modules.

Current module layout:

```text
apps
  settings.gradle
  build.gradle
  api
  shared
  documents
  images
  videos
  audio
  knowledge
  orchestrator
  storage
  files
  users
  workspaces
```

Run build commands from `apps`:

```bash
./gradlew test
```

`apps/api` is the executable Spring Boot application.

`apps/shared` is a Java library module for data models that are genuinely shared across application modules.

Business logic modules:

- `apps/documents` - document ingestion, metadata, text extraction, and document lifecycle.
- `apps/images` - image ingestion, OCR, image analysis, and image metadata.
- `apps/videos` - video ingestion, frame extraction, scene analysis, and video understanding.
- `apps/audio` - audio ingestion, speech-to-text, text-to-speech, diarization, and audio metadata.
- `apps/knowledge` - knowledge organization, retrieval, and future knowledge-base workflows.
- `apps/orchestrator` - async coordination across multimodal business modules.
- `apps/storage` - file storage abstraction and storage implementations.
- `apps/files` - workspace file metadata, file lifecycle/status, and storage-backed file access.
- `apps/users` - user accounts, authentication support, and ownership identity.
- `apps/workspaces` - workspace metadata and workspace lifecycle.

The initial Java package is `com.aiworkspace`.

Start with these package areas inside `apps/api`:

```text
com.aiworkspace
  config
  controllers
```

Keep `apps/api` as the HTTP entrypoint and put business logic in the relevant business module.

## Reading Order

- `vision.md` - product vision and long-term platform direction.
- `development-philosophy.md` - engineering mindset and current MVP stage.
- `architecture.md` - modular monolith architecture and module boundaries.
- `technology-stack.md` - current and future technical stack.
- `coding-standards.md` - code structure, style, and error handling rules.
- `testing.md` - unit and integration testing expectations.
- `api-guidelines.md` - REST API design expectations.
- `database.md` - PostgreSQL and Flyway rules.
- `ai-layer.md` - AI provider abstraction and multimodal design direction.
- `roadmap.md` - future capabilities and infrastructure.
- `definition-of-done.md` - completion criteria for implementation tasks.
- `git-workflow.md` - branch, commit, push, and merge rules.
- `architecture-decisions.md` - ADR rules for important architectural choices.
- `security.md` - secrets, sensitive data, and access-control expectations.
- `observability.md` - logging, metrics, tracing, and diagnosability rules.
- `ai-quality.md` - prompt, model, retrieval, and AI-output quality rules.
- `module-boundaries.md` - modular monolith boundary rules.
- `dependencies.md` - dependency selection and justification rules.
- `deployment.md` - deployment preference and operational assumptions.
- `risky-actions.md` - actions that require explicit confirmation.

## Prompt For Future Agents

Use this prompt when handing the repository to another AI agent:

```text
You are working on this repository as a senior engineer building a long-term commercial AI Platform product.

Before making changes, read:

1. AGENTS.md
2. DEVELOPER_CODE_PREFERENCES.md
3. docs/agent-context/README.md
4. The specific files in docs/agent-context/ that are relevant to the task.

DEVELOPER_CODE_PREFERENCES.md captures Andrii's code-writing preferences and has higher priority than the general docs/agent-context guidance unless Andrii explicitly says otherwise.

Follow the architecture, coding standards, testing rules, database rules, API guidelines, AI-layer guidelines, security rules, Git workflow, and definition of done described there.

Do not treat this as a one-off code generation task. Make maintainable, production-oriented decisions.
```

Short version:

```text
Use AGENTS.md as your primary instruction file. Then read DEVELOPER_CODE_PREFERENCES.md, docs/agent-context/README.md, and relevant project guidance files before coding. Act as a senior engineer on a long-term commercial product, not as a snippet generator.
```
