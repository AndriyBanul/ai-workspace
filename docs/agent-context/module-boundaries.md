# Module Boundaries

- `apps` is the Gradle root for application modules.
- `apps/api` is the only executable Spring Boot application at the current stage.
- `apps/shared` is a Java library module for genuinely shared data models.
- `apps/documents`, `apps/images`, `apps/videos`, `apps/audio`, `apps/knowledge`, `apps/orchestrator`, and `apps/workspaces` are business logic modules.
- Do not add nested `settings.gradle` files or Gradle wrappers inside subprojects.
- The initial API package is `com.aiworkspace`.
- Start with package-level areas inside `apps/api`: `config` and `controllers`.
- Add more physical Gradle modules only when real domain complexity requires them.
- Keep `shared` small and intentional; it must not become a dumping ground.
- Keep HTTP controllers in `apps/api`; keep business logic in the relevant business module.
- Each module should own its domain model and persistence rules.
- Workspace source metadata, source status transitions, and uploaded raw byte
  storage belong in `apps/workspaces`.
- Cross-media ingestion sequencing belongs in `apps/orchestrator`; media modules
  extract or generate content, while the orchestrator coordinates source
  creation, processing, knowledge indexing, completion/failure, and reprocessing.
- AI provider integrations should be exposed to services through capability-oriented interfaces in the owning module's `interfaces` package.
- Avoid broad generic AI provider abstractions; prefer focused ports such as speech-to-text, text-to-speech, image understanding, image generation, video understanding, and video generation.
- Cross-module communication should happen through interfaces or application services.
- Avoid direct access to another module's repositories or internal entities.
- Keep module APIs small, intentional, and stable.
- Avoid circular dependencies between modules.
- If module boundaries become unclear, stop and propose a cleaner design.

Future microservices should map naturally to existing modules, so module boundaries matter even in the modular monolith stage.
