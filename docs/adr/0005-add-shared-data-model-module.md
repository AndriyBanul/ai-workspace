# ADR 0005: Add Shared Data Model Module

## Status

Accepted

## Context

The project uses an apps-level Gradle multi-module structure rooted at `apps`.

The API module is the executable Spring Boot application. The project also needs a place for data models that are shared across application modules.

These shared models should not live inside `apps/api`, because that would make other future modules depend on the executable API application.

## Decision

Add `apps/shared` as a Java library Gradle module.

Register it in `apps/settings.gradle`:

```gradle
include 'shared'
```

Use this package structure:

```text
apps/shared
  build.gradle
  src/main/java/com/aiworkspace/shared/model
  src/test/java/com/aiworkspace/shared
```

Make `apps/api` depend on `apps/shared`:

```gradle
implementation project(':shared')
```

## Scope

`shared` may contain:

- Shared data models.
- Stable value objects used by multiple modules.
- Common API contracts only when they are genuinely cross-module.

`shared` must not contain:

- REST controllers.
- Application startup code.
- Persistence implementation.
- Business workflow orchestration.
- Random helpers that do not have a stable shared purpose.

## Alternatives Considered

### Keep Shared Models Inside `apps/api`

This is simpler initially, but it makes future modules depend on the executable API application or duplicate shared model code.

### Delay The Shared Module

This avoids upfront structure, but the user explicitly wants a shared module for common data models in the IntelliJ multi-module project.

## Consequences

Positive consequences:

- IntelliJ IDEA shows `shared` as a separate module.
- Shared data models have a clear home.
- Future modules can depend on `shared` without depending on `api`.
- The executable application stays focused on API startup and HTTP entry points.

Trade-offs:

- The project now has an additional Gradle module.
- The team must keep `shared` small and intentional.
- Shared model changes can affect multiple modules, so they require extra care.
