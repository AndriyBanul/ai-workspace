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
  knowledge
  orchestrator
  workspaces
```

`apps` is the Gradle root opened by IntelliJ IDEA.

`apps/api` is the executable Spring Boot application subproject.

`apps/shared` is a Java library subproject for shared data models.

Business logic lives in dedicated Java library subprojects:

- `apps/documents`
- `apps/images`
- `apps/videos`
- `apps/audio`
- `apps/knowledge`
- `apps/orchestrator`
- `apps/workspaces`

`apps/orchestrator` owns async multimodal ingestion coordination and ingestion
job lifecycle tracking. Ingestion job and step status are application state and
are persisted in PostgreSQL through Flyway-managed tables. Extracted multimodal
knowledge is stored as append-only knowledge items in OpenSearch through the
`apps/knowledge` module.

Every ingestible input is also a workspace source. Uploaded documents, audio,
images, and videos reference file storage; web pages and YouTube videos are
URL-backed sources. All API ingestion paths pass through the orchestrator's
source lifecycle coordinator: create, process, index, complete/fail, then
optional reprocess or delete. A stable workspace source ID is also the
OpenSearch source ID, making replacement and deletion idempotent across media.

`apps/orchestrator` also owns a PostgreSQL-backed source recovery ledger. It
records processing and deletion attempts independently from source metadata,
leases in-flight work, retries transient failures with bounded backoff, and
dead-letters permanent or exhausted failures. A scheduled reconciler compares
completed sources with raw file storage and OpenSearch, then reprocesses missing
knowledge, reports missing source bytes, or removes orphaned knowledge. Source
processing uses replacement indexing and deletion is delete-if-present, so both
recovery paths are idempotent.

`apps/workspaces` owns workspace metadata persisted in PostgreSQL. Business
flows that attach knowledge to a workspace should use a real `workspaceId`
instead of hardcoded workspace identifiers.

`apps/knowledge` exposes `KnowledgeService` as its stable workflow facade.
Internally, indexing, retrieval, answer generation, and citation formatting are
separate services. Its OpenSearch repository adapter follows the same boundary:
the public repository client delegates search/read and index/write operations to
focused collaborators, with index lifecycle and document mapping kept in shared
OpenSearch infrastructure.

The indexed `KnowledgeItem` aggregate is composed rather than flattened. Source
provenance, chunk location, and embedding metadata use separate immutable value
objects. OpenSearch keeps the existing flat document field names as its storage
schema, so this domain refactor does not require reindexing existing content.

The initial API package is `com.aiworkspace`.

The React frontend is organized by user workflow. `App` only selects the
authenticated or unauthenticated application. Workspace shell/navigation,
workspace content routing, Library source workflows, and Studio media workflows
use focused components. Feature-specific components live under `src/library`
and `src/studio`; shared visual primitives and action state remain in
`components.jsx`. Keep API calls at workflow-container boundaries so child
components remain testable without duplicating backend behavior.

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

Additional business modules such as `ai` should be added when their boundaries become concrete.
