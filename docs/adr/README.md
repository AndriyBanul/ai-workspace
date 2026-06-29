# Architecture Decision Records

Use this directory for important architecture decisions.

Create ADRs when a decision affects module boundaries, infrastructure, database strategy, AI provider design, deployment architecture, or long-term maintainability.

Current ADRs:

- `0001-use-modular-monolith.md` - accepted initial architecture.
- `0002-define-initial-module-boundaries.md` - superseded by ADR 0004.
- `0003-add-documents-module.md` - superseded by ADR 0004.
- `0004-use-apps-level-gradle-multi-module-structure.md` - accepted current application structure.
- `0005-add-shared-data-model-module.md` - accepted shared library module for common data models.
- `0006-add-multimodal-business-modules.md` - accepted business modules for documents, images, videos, and audio.

Suggested file name:

```text
0001-short-decision-title.md
```

Suggested structure:

```md
# ADR 0001: Short Decision Title

## Status

Proposed | Accepted | Superseded

## Context

What problem or constraint led to this decision?

## Decision

What did we decide?

## Alternatives Considered

What other options were considered?

## Consequences

What trade-offs, risks, or follow-up work does this create?
```
