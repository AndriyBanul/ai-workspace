# Architecture

Current architecture:

Modular Monolith.

The application code uses an apps-level Gradle multi-module structure:

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
```

`apps` is the Gradle root opened by IntelliJ IDEA.

`apps/api` is the executable Spring Boot application subproject.

`apps/shared` is a Java library subproject for shared data models.

Business logic lives in dedicated Java library subprojects:

- `apps/documents`
- `apps/images`
- `apps/videos`
- `apps/audio`

The initial API package is `com.aiworkspace`.

Initial package areas inside `apps/api` are intentionally small:

```text
com.aiworkspace
  config
  controllers
```

## Rules

- One deployable application.
- One database.
- One Git repository.
- Use `apps` as the single Gradle root for application modules.
- Keep `apps/api` as the executable Spring Boot application.
- Keep `apps/shared` limited to genuinely shared data models and stable shared contracts.
- Keep business logic out of `apps/api`; place it in the relevant business module.
- Do not add nested Gradle roots or wrappers inside subprojects.
- Separate modules for each business domain.
- Modules must communicate through interfaces.
- Internal APIs should remain clean and well-defined.

Future migration to microservices should require minimal code changes.

Each future microservice should correspond to one existing module.

Additional business modules such as `knowledge` and `ai` should be added when their boundaries become concrete.
