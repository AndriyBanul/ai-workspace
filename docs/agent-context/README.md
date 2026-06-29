# Agent Context

This directory contains long-term project memory for AI agents working on this repository.

Use it together with the root `AGENTS.md` file.

## How To Use This Context

Before making changes, read:

1. `AGENTS.md`
2. `docs/agent-context/README.md`
3. The specific files in this directory that are relevant to the task.

Act as a senior engineer working on a long-term commercial AI Platform product, not as a coding assistant generating isolated snippets.

Make maintainable, production-oriented decisions.

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

## Prompt For Future Agents

Use this prompt when handing the repository to another AI agent:

```text
You are working on this repository as a senior engineer building a long-term commercial AI Platform product.

Before making changes, read:

1. AGENTS.md
2. docs/agent-context/README.md
3. The specific files in docs/agent-context/ that are relevant to the task.

Follow the architecture, coding standards, testing rules, database rules, API guidelines, and AI-layer guidelines described there.

Do not treat this as a one-off code generation task. Make maintainable, production-oriented decisions.
```

Short version:

```text
Use AGENTS.md as your primary instruction file. Then read docs/agent-context/README.md and relevant project guidance files before coding. Act as a senior engineer on a long-term commercial product, not as a snippet generator.
```
