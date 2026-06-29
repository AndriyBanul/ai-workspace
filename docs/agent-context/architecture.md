# Architecture

Current architecture:

Modular Monolith.

The application code uses an apps-level Gradle multi-module structure:

```text
apps
  settings.gradle
  build.gradle
  api
```

`apps` is the Gradle root opened by IntelliJ IDEA.

`apps/api` is the executable Spring Boot application subproject.

The initial API package is `com.aiworkspace`.

Initial package areas inside `apps/api` are intentionally small:

```text
com.aiworkspace
  config
  platformapi
  shared
```

## Rules

- One deployable application.
- One database.
- One Git repository.
- Use `apps` as the single Gradle root for application modules.
- Keep `apps/api` as the executable Spring Boot application.
- Do not add nested Gradle roots or wrappers inside subprojects.
- Separate modules for each business domain when real domain complexity requires a physical module boundary.
- Modules must communicate through interfaces.
- Internal APIs should remain clean and well-defined.

Future migration to microservices should require minimal code changes.

Each future microservice should correspond to one existing module.

Dedicated business modules such as `documents`, `knowledge`, and `ai` should be added later, not as empty upfront structure.
