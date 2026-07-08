# Developer Code Preferences

This file captures Andrii's code-writing preferences for AI agents working on this repository.

These preferences have higher priority than the general guidance in `docs/agent-context/`.
If this file conflicts with other project guidance, follow this file first unless the user explicitly says otherwise.

## Engineering Style

- Build as a long-term commercial product, not as isolated snippets.
- Prefer clean, maintainable, production-oriented code over quick hacks.
- Keep implementations simple, but not under-designed.
- Follow existing project style before introducing new patterns.
- Avoid unnecessary abstractions until they reduce real complexity.

## Module Boundaries

- `apps/api` is the HTTP entrypoint only.
- Business logic must live in the relevant business module:
  - `apps/audio`
  - `apps/documents`
  - `apps/images`
  - `apps/videos`
  - `apps/knowledge`
  - `apps/orchestrator`
  - `apps/workspaces`
- Keep shared models in `apps/shared` only when they are genuinely shared.
- Avoid circular dependencies between business modules.
- Provider-specific code should stay behind module clients/services.
- Code should make module ownership obvious from package names and constructor dependencies.

## Package Conventions

- `controllers` — HTTP endpoints and request/response boundary.
- `services` — business flow and orchestration.
- `validators` — reusable service-level validation.
- `interfaces` — service, boundary, and capability interfaces, including AI provider ports.
- `repositories` — Spring Data repositories, repository ports, and repository adapters.
- `entities` — JPA entities only.
- `mappers` — MapStruct mappers and recurring mapping concerns.
- `client` — concrete external provider/client integrations.
- `config` — Spring configuration and typed configuration properties.
- Do not place JPA entities in `repositories`.
- Do not place concrete provider/client implementations in `interfaces`.

## Naming

- Prefer general module-level controller and service names:
  - `AudioController` / `AudioService`
  - `DocumentController` / `DocumentService`
  - `ImageController` / `ImageService`
  - `VideoController` / `VideoService`
- Avoid overly narrow controller/service names when one module-level class can clearly own related operations.
- Keep endpoint paths stable even when internal class names change.

## Controllers

- Controllers should stay thin.
- Controllers handle HTTP concerns only:
  - request/response mapping
  - status codes
  - multipart/body parsing
  - response headers
- Controllers should delegate business logic to services.
- Keep API response models explicit and readable.

## Services

- Services own validation and business flow.
- Services call provider clients, but should not expose provider DTOs.
- Keep service methods focused and named by product behavior.
- Prefer constructor injection.
- Keep validation separate from normalization/trimming for required input values:
  - use dedicated validation methods such as `validateName(name)`, `validateWorkspaceId(workspaceId)`, and `validateJobId(jobId)`;
  - call `.trim()` at the point where the value is passed further, such as into an entity, repository query, provider call, or response model.
- Keep optional normalization separate when it has different semantics, for example blank optional fields becoming `null`.

## Mapping

- Use MapStruct for recurring mapping boundaries, especially:
  - entity to domain model;
  - domain model to response/details DTO;
  - domain model to entity.
- Keep MapStruct mappers in a dedicated `mappers` package inside each module, for example `com.aiworkspace.workspaces.mappers`.
- Do not force MapStruct into custom parsing, fallback, provider-response interpretation, or JSON tree extraction when explicit hand-written code is clearer.
- Mappers should own object conversion; services should own business flow.

## Models And Entities

- Use records for simple immutable DTO/model objects.
- Add Lombok `@Builder` to model records and entity classes when it improves construction readability in tests and business code.
- For JPA entities, prefer Lombok boilerplate:
  - `@Getter`
  - `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
  - `@AllArgsConstructor`
  - `@Builder`
- Avoid hand-written entity constructors when Lombok annotations express the same intent clearly.
- Annotate Spring Data `JpaRepository` interfaces with `@Repository` explicitly for consistency and readability.

## HTTP Clients

- Use a shared Spring `RestClient` bean for HTTP provider calls.
- Inject `RestClient` into provider clients instead of creating new HTTP clients inside each class.
- Use a shared Spring `ObjectMapper` bean for JSON parsing/serialization.
- Inject `ObjectMapper` into clients/services instead of calling `new ObjectMapper()` in runtime code.
- Keep raw socket clients only when protocol requires it, for example Piper Wyoming protocol.
- Provider clients should wrap provider-specific REST details.

## AI Providers

- Keep provider-specific logic isolated in clients.
- Domain services should depend on capability-oriented provider interfaces in module `interfaces` packages, not concrete Gemini, Veo, FLUX, Whisper, or Piper client classes.
- Prefer focused ports for each capability instead of one broad `AiProvider` abstraction.
- Do not leak Gemini, Veo, FLUX, Whisper, or other provider DTOs into controllers.
- Avoid provider-specific names for provider-neutral models or interfaces.
- Use provider-specific names only for concrete implementations.
- Keep provider configuration in `application.properties` and environment variables.
- API keys must come from environment variables, never from committed code.
- Provider-not-configured and provider-failed cases should produce clear errors, not generic runtime failures.

## Configuration

- Prefer environment variables for secrets.
- Document config in `.env.example` without real values.
- Use explicit properties for provider models and base URLs.
- Prefer typed config properties over hardcoded operational values such as timeouts, HTTP settings, provider settings, and user agents.
- Centralize shared infrastructure beans such as `RestClient`, `ObjectMapper`, storage implementations, and provider clients.
- Do not commit secrets.

## File Storage

- Access raw file bytes through a storage abstraction, not direct filesystem paths in business logic.
- Use a `FileStorage` interface so local filesystem, S3, R2, B2, or MinIO implementations can be added or swapped without changing callers.
- For the MVP, prefer local filesystem storage configured through `ai-workspace.storage.local.root`.
- Store file metadata in PostgreSQL and raw file bytes in storage; do not store large raw files in PostgreSQL `bytea`.
- Store files under generated storage keys based on internal IDs, not original filenames.

## Testing

- Run `cd apps && ./gradlew test` after code changes.
- Add focused unit tests for service validation and business behavior.
- Add ownership/security tests for workspace-scoped endpoints.
- Use context tests to catch Spring wiring issues.
- Test coverage should scale with risk and module impact.
- Run `cd apps && ./gradlew clean test` after refactors involving annotation processing, MapStruct, Lombok, generated code, or package moves.
- Run `cd apps && ./gradlew :api:bootJar` after runtime wiring, configuration, dependency, or package changes.
- Keep `git diff --check` clean before committing.

## Git Workflow

- Work on `develop` by default.
- Push `develop` after completed changes.
- Do not touch `main` unless explicitly requested.
- Keep commits focused and named clearly.
- Keep the working tree clean after finishing.

## Deployment Preferences

- Prefer native/system package installs for server software by default.
- Use Docker only when explicitly requested.
- Preserve existing configs and inspect current state before changing system config.

## Style Details

- Keep code readable and self-explanatory.
- Use records for simple immutable DTO/model objects.
- Avoid broad utility classes unless they remove real duplication.
- Avoid logging sensitive content or secrets.
- Prefer stable, boring production code over clever code.
- Prefer typed config properties over hardcoded operational values such as timeouts, HTTP settings, provider settings, and user agents.
- Use global exception handling with a consistent API error response instead of repeated controller-level try/catch blocks.
- Keep changes scoped to the module and behavior being touched.
- Do not mix unrelated refactors into feature commits unless needed for the change.

## Errors And Observability

- Use global API exception handling for consistent error responses.
- Log important workflow identifiers such as `workspaceId`, `fileId`, `jobId`, and provider name.
- Ingestion jobs should expose status, step errors, timestamps, and retryable failures.
- Avoid logging raw file contents, prompts, secrets, API keys, or sensitive user data.
