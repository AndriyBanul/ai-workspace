# ADR 0002: Define Initial Module Boundaries

## Status

Accepted

## Context

The project is starting its implementation phase after establishing the repository, Git workflow, and project guidance.

The architecture is a Modular Monolith, but the MVP should not introduce empty business modules before the domain is real.

At this stage, the project needs a minimal structure that supports:

- Shared models and common API response concepts.
- Centralized technical configuration.
- A single REST API entry point.
- Future addition of business modules when actual domain features appear.

## Decision

Start with three initial module areas inside the API application:

- `shared`
- `config`
- `platformapi`

Use Java package name `platformapi` even if the conceptual module name is `platform-api`, because Java package names cannot contain hyphens.

Initial package structure:

```text
com.aiworkspace
  shared
    model
    error
    response
    pagination

  config
    web
    openapi
    persistence
    security
    observability

  platformapi
    web
      health
      info
      system
    dto
```

## Responsibilities

### `shared`

Contains only genuinely shared concepts:

- Common response models.
- Error codes.
- Pagination models.
- Base value objects.
- Shared interfaces.

`shared` must not become a dumping ground for unrelated helpers.

### `config`

Contains technical application configuration:

- Spring configuration.
- OpenAPI configuration.
- Jackson and web configuration.
- Persistence and Flyway configuration.
- Security configuration when needed.
- Observability and logging configuration when needed.

`config` must not contain business logic.

### `platformapi`

Acts as the initial REST API entry point:

- Controllers.
- API-level request and response DTOs.
- Health, info, and system endpoints.
- API mapping and adapter code.

`platformapi` must not accumulate business logic.

When the first real domain appears, business logic should move into a dedicated business module such as `documents`, `knowledge`, or `ai`.

## Alternatives Considered

### Create All Future Modules Immediately

Creating modules such as `documents`, `knowledge`, `ai`, `workflows`, `agents`, and `integrations` immediately would make the future direction visible, but it would also create empty structure without real domain pressure.

This increases the risk of premature abstraction.

### Put Everything Under `platformapi`

This is initially simple, but it would make `platformapi` a catch-all module and blur boundaries between API adapters, configuration, shared concepts, and future domain logic.

## Consequences

Positive consequences:

- Minimal starting structure.
- Clear technical separation.
- Low upfront complexity.
- Easy addition of future domain modules.
- Better protection against premature architecture.

Trade-offs:

- The first business feature will require adding a new module.
- Agents must avoid putting domain logic into `platformapi`.
- `shared` must be kept intentionally small.

## Follow-Up Rules

- Add business modules only when real domain features require them.
- Keep `platformapi` focused on REST entry points and API adapters.
- Keep `config` technical only.
- Keep `shared` small and stable.
- Create new ADRs before introducing major new module boundaries.
