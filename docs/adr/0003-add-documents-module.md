# ADR 0003: Add Documents Module

## Status

Superseded by [ADR 0004: Use Apps-Level Gradle Multi-Module Structure](0004-use-apps-level-gradle-multi-module-structure.md)

## Context

This ADR is kept for historical context only. The decision was superseded before document-related implementation started.

The project now starts with a lighter `api` module containing `config` and `controllers` package areas. A dedicated `documents` module will be introduced later only when real document-domain complexity requires it.

The initial API structure contains `shared`, `config`, and `platformapi`.

The project now needs its first business module for working with text documents. This is the first step toward future knowledge management features such as document ingestion, text extraction, chunking, semantic search, and RAG.

The module should be introduced without adding premature implementation details.

## Decision

Add a `documents` module under the API application package:

```text
com.aiworkspace
  documents
    domain
      model
      service
      event
    application
      command
      query
      port
      usecase
    infrastructure
      persistence
      external
    web
      controller
      dto
```

The `documents` module owns the document domain and should be responsible for text document workflows such as:

- document metadata
- text content lifecycle
- document processing status
- document-related commands and queries
- document persistence abstractions
- document REST API adapters

## Boundary Rules

- `documents` owns its domain model and persistence rules.
- `platformapi` must not contain document business logic.
- Other modules must not access `documents.infrastructure` directly.
- Cross-module access should happen through `documents.application` ports, use cases, or intentional public application services.
- Future `knowledge` or `ai` modules should integrate with `documents` through stable application contracts, not internal entities.

## Alternatives Considered

### Keep Document Logic In `platformapi`

This would be faster initially, but it would turn `platformapi` into a business module and blur the REST entry point with domain logic.

### Wait Until Full Document Features Exist

Waiting would keep the structure smaller, but the project is ready to introduce the first real domain boundary because text documents are a core part of the AI Platform product.

## Consequences

Positive consequences:

- Establishes the first real business module boundary.
- Keeps document logic out of `platformapi`.
- Creates a natural future integration point for `knowledge` and `ai`.
- Preserves the Modular Monolith direction from ADR 0001.

Trade-offs:

- Adds structure before full document behavior exists.
- Requires discipline to keep module dependencies clean.
- Future implementation should avoid filling every package until there is real code pressure.
