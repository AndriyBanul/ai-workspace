# ADR 0004: Use Apps-Level Gradle Multi-Module Structure

## Status

Accepted

## Context

The project should open in IntelliJ IDEA as one Gradle project with multiple modules.

The repository contains product documentation, infrastructure files, scripts, data folders, and application code. The application code lives under `apps/`.

The API application has been generated as a Spring Boot project, but the initial generated structure introduced two Gradle roots:

- `apps`
- `apps/api`

This creates ambiguity about which Gradle project should be opened, built, and used by CI.

## Decision

Use `apps` as the single Gradle root for application modules.

The `api` application is a Gradle subproject:

```text
apps
  settings.gradle
  build.gradle
  gradlew
  gradlew.bat
  gradle/wrapper

  api
    build.gradle
    src/main/java/com/aiworkspace
    src/test/java/com/aiworkspace
```

The initial Java package is:

```text
com.aiworkspace
```

The initial API package areas are intentionally small:

```text
com.aiworkspace
  config
  controllers
```

Do not add dedicated business modules until real domain complexity requires them.

## Responsibilities

### `apps`

Owns the Gradle multi-module build:

- module inclusion
- shared repositories
- shared Java toolchain configuration
- common build conventions

### `apps/api`

Owns the executable Spring Boot application:

- application entry point
- REST API entry points
- application configuration
- initial MVP code

Spring Boot application plugins belong in `apps/api`, not in every subproject.

### Future Subprojects

Future modules may be added under `apps/` as library modules, for example:

```text
apps/shared
apps/documents
apps/ai
apps/knowledge
```

They should be added only when there is enough code pressure to justify a physical Gradle module boundary.

## Alternatives Considered

### Use `apps/api` As A Standalone Gradle Project

This is simpler for one application, but it does not satisfy the requirement that IntelliJ IDEA opens one project with multiple modules.

### Put Gradle Build Files At Repository Root

This is also a valid multi-module layout, but the repository contains non-application folders such as documentation, infrastructure, scripts, and data. Keeping the application Gradle root under `apps` keeps the build focused.

## Consequences

Positive consequences:

- IntelliJ IDEA can open `apps` as one Gradle project.
- `api` is a normal subproject instead of a nested standalone Gradle root.
- Future modules can be added cleanly under `apps`.
- There is one Gradle entry point for local development and CI.

Trade-offs:

- Developers should run Gradle commands from `apps`.
- Repository-level documentation and infrastructure remain outside the Gradle root.
- Future automation must be explicit about using `apps` as the working directory.

## Follow-Up Rules

- Run build commands from `apps`, for example `./gradlew test`.
- Do not add nested `settings.gradle` files inside subprojects.
- Do not add Gradle wrappers inside subprojects.
- Add future modules to `apps/settings.gradle`.
- Keep `apps/api` as the only executable Spring Boot application until another deployable app is intentionally introduced.
