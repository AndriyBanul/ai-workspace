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
- Keep shared models in `apps/shared` only when they are genuinely shared.
- Avoid circular dependencies between business modules.
- Provider-specific code should stay behind module clients/services.

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

## HTTP Clients

- Use a shared Spring `RestClient` bean for HTTP provider calls.
- Inject `RestClient` into provider clients instead of creating new HTTP clients inside each class.
- Keep raw socket clients only when protocol requires it, for example Piper Wyoming protocol.
- Provider clients should wrap provider-specific REST details.

## AI Providers

- Keep provider-specific logic isolated in clients.
- Do not leak Gemini, Veo, FLUX, Whisper, or other provider DTOs into controllers.
- Keep provider configuration in `application.properties` and environment variables.
- API keys must come from environment variables, never from committed code.

## Configuration

- Prefer environment variables for secrets.
- Document config in `.env.example` without real values.
- Use explicit properties for provider models and base URLs.
- Do not commit secrets.

## Testing

- Run `cd apps && ./gradlew test` after code changes.
- Add focused unit tests for service validation and business behavior.
- Use context tests to catch Spring wiring issues.
- Test coverage should scale with risk and module impact.

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
