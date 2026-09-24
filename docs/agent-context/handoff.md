# Active Handoff

## Architecture improvement sequence (2026-09-23)

- Step 1: Spring Security now permits the Vite `/assets/**` bundle and no longer
  lists the removed static demo files. `ApiIntegrationTest` loads `/` and each
  referenced asset anonymously from the Spring app.
- Step 2: `.github/workflows/ci.yml` checks frontend tests/build, clean Gradle
  tests plus bootJar, and changed-line whitespace on PRs and develop pushes.
- Durable ingestion: asynchronous submissions persist recovery rows linked to
  their original jobs before after-commit dispatch. Workers queue source IDs in
  a bounded executor. `V8` links jobs; `V9` adds per-attempt fencing tokens.
  Leases renew through queue wait and provider work; stale workers cannot commit.
  `V11` and `Idempotency-Key` make async upload/reprocess retries replay the
  original response (30-day retention). Multi-instance fencing tests pass.
- Versioned indexing: `V10` stores active source manifests. OpenSearch staging
  waits for refresh and verifies exact generation count. The source/job/manifest
  completion transaction publishes one active generation; readers hide staged
  versions, and reconciliation checks completeness and prunes inactive versions.
- Frontend state: workspace routes are bookmarkable, activity jobs and answer
  history are persisted server-side (`V12` for answers), API errors share one
  handler, 401 clears browser credentials, and views load lazily.
- Cleanup: removed obsolete media-service ingestion paths, simplified production
  orchestrator injection, added typed source processing results and the Gradle
  `verifyModuleBoundaries` check. CI runs a strict JSDoc/TypeScript check of the
  frontend API and route modules; broader React typing/formatting remains
  incremental rather than a complete TypeScript conversion.
- Safeguards: `V13` adds PostgreSQL daily per-user quotas, request IDs flow into
  response headers/logs, Micrometer exposes ingestion/recovery/worker metrics,
  and the production profile enforces HTTPS. HTTP Basic and process-local IP
  rate limiting still require managed identity and shared edge controls for a
  sensitive multi-node production deployment.
- Verification: clean Gradle tests, module boundary check, Spring bootJar,
  frontend tests/build, whitespace check, and the isolated real OpenSearch suite
  passed. The latter includes staged-generation visibility and pruning.
- The Mantine UI work is part of this delivery. Inspect `git status --short`
  for newer local work rather than relying on this historical status note.

## Mantine frontend integration (2026-09-23)

- The Mantine design reference in `docs/design/mantine-workspace` is now implemented
  in `apps/web`. The design prototype remains fixture-only; the application uses
  the existing authenticated APIs for all Library, Ask, Studio, and Activity flows.
- Library has one Add sources dialog for background/direct files, web pages, and
  YouTube, plus metadata/recovery details, reprocessing, deletion, search, and
  status mapping. At initial integration, Ask kept session-only history and
  Activity kept local job IDs; the architecture sequence above replaced both
  with persisted API reads. Studio retains generation and direct analysis.
- Mantine core/hooks and Tabler icons are pinned in `apps/web`; obsolete CSS and
  superseded upload components were removed. A standalone Mantine theme and
  CSS module own the new presentation. No backend contract was changed.
- Frontend tests/build and full Gradle tests/bootJar pass. Desktop/mobile visual
  smoke checks with mocked API responses showed no browser page errors. A live
  provider-backed end-to-end run remains separate work.
- Fixed `apps/api` resource packaging so `processResources` tracks the Vite
  output and rebuilds the Spring JAR when frontend assets change. Removed the
  superseded static demo. A clean Gradle test/bootJar build passed, and the
  resulting JAR contains the current Vite `index.html` and hashed assets.
- The UI implementation and design handoff are included with this delivery.

## End-to-end regression specification (2026-09-24)

- `docs/end-to-end-test-specification.md` now defines agent execution rules,
  isolated fixtures, 58 case IDs, and 17 positive plus two negative grounded
  answer questions. It separates deterministic checks from live provider and
  fault-injection profiles, with evidence and cleanup requirements.
- Audio segments are indexed as timed chunks; uploaded video and YouTube
  currently index one combined summary/transcript item. The specification
  tests these different contracts without requiring video citation timestamps.
- The full live provider-backed regression suite has not been run. It needs
  disposable dependencies, controlled public HTML/YouTube fixtures, and an
  approved provider spend cap.

This file is the durable handoff for AI-agent sessions on this repository.
Use it when a chat is long, when Codex compaction fails, or when work must
continue in a new chat.

## How To Use

At the start of a new session:

1. Read `AGENTS.md`.
2. Read `DEVELOPER_CODE_PREFERENCES.md`.
3. Read `docs/agent-context/README.md`.
4. Read this file.
5. Run `git status --short` before assuming what changed.

Before ending a long session:

1. Update the sections below with the current focus, completed work, open
   decisions, verification, and next steps.
2. Prefer a concise handoff here over relying on remote `/compact` when the
   chat is already large.

## Codex Compaction Note

On 2026-08-30, Codex in IntelliJ IDEA reported:

```text
Error running remote compact task: unexpected status 404 Not Found: {"detail":"Not Found"}, url: https://chatgpt.com/backend-api/codex/responses/compact
```

Local checks at that time:

- `codex --version` returned `codex-cli 0.137.0`.
- No `~/.codex/config.toml` was found.
- No project `.codex/config.toml` was found.

Treat this as a Codex backend, account, rollout, or IDE/CLI version issue unless
new evidence points elsewhere. Practical workaround: write or update this
handoff, start a fresh chat, and ask the next agent to continue from this file.
If the issue repeats, capture the timestamp, request id, `cf-ray`, IDE plugin
version, `codex --version`, and whether the session was local, worktree, cloud,
or remote.

## Current Focus

- Roadmap items 6, 7, and 8 are implemented. The HTTP security baseline,
  durable source recovery/reconciliation, frontend component/workflow-test
  refactor, and Mantine UI are in this delivery. Check Git for subsequent work.

## Completed

- Added this handoff file so future chats can continue without depending on
  remote compaction.
- Replaced Gemini embedding generation with Spring AI 2.0.1 and the pinned
  `intfloat/multilingual-e5-small` ONNX model.
- Added E5 passage/query prefixes, batching, vector validation, and L2
  normalization behind `TextEmbeddingProvider`.
- Changed the vector dimension from 768 to 384 and moved OpenSearch storage to
  the fresh `knowledge-items-v2` index.
- Kept ingestion atomic with respect to embedding generation: all vectors are
  created before a chunk set is indexed. Query embedding failures still fall
  back to BM25.
- Added offline application tests and successfully ran the real model on Apple
  ARM, producing a finite 384-dimensional vector.
- Aligned Spring Boot from 4.1.0 to 4.1.1 because Spring AI 2.0.1 targets that
  patch release.
- Recorded the dependency and architecture decision in ADR 0007.

## In Progress

- No implementation work remains for local embedding generation. The working
  tree contains the uncommitted implementation and documentation changes.

## Open Decisions

- Do not commit or push the local embedding changes unless Andrii explicitly
  asks.
- Production sizing should later measure embedding latency, concurrency, CPU,
  memory, and startup/cache behavior on the deployment host.

## Verification

- `cd apps && ./gradlew clean test` passed after aligning Spring Boot.
- `cd apps && ./gradlew :knowledge:test :api:test :api:bootJar` passed after the
  final dependency placement and `knowledge-items-v2` migration.
- A standalone smoke test loaded the pinned ONNX model through Spring AI and
  produced `dimensions=384 finite=true` on macOS ARM.
- `git diff --check` passed before the final documentation updates.

## Next Steps

- Review the combined item 6, item 7, and item 8 working-tree changes with
  Andrii.
- Commit and push only when requested.

## Durable source recovery and reconciliation (2026-09-16)

- Added PostgreSQL `source_recovery_tasks` through Flyway V7 with one durable
  PROCESS/DELETE task per source, bounded attempts, next-attempt timestamps,
  running leases, bounded errors, and explicit dead-letter state.
- Source processing and deletion now record attempts and outcomes. Transient I/O
  and upstream failures use bounded exponential backoff; permanent failures and
  exhausted retries dead-letter. Recovery-recording failures no longer mask the
  original workflow exception.
- A scheduled orchestrator recovery worker retries due or lease-expired work and
  periodically reconciles source metadata, file storage, and OpenSearch.
  Replacement indexing and delete-if-present behavior keep retries idempotent.
- Added an owner-scoped source recovery API and Library detail fields for status,
  attempts, next check, and last error. Configuration is documented in
  `.env.example`; ADR 0010 records the eventual-consistency decision.
- Focused recovery tests, a clean full Gradle test suite and `:api:bootJar`,
  frontend tests/build, OpenAPI YAML parsing, and `git diff --check` pass.

## Frontend component and workflow-test refactor (2026-09-16)

- Reduced `App.jsx` to authentication-state routing and moved login, workspace
  shell/sidebar, workspace content routing, and persistent job state into
  focused components.
- Split Library ingestion, web import, source list/filtering, results, recovery
  details, and deletion confirmation into workflow-oriented components under
  `src/library`.
- Split Studio generation, media analysis, YouTube import, transcript display,
  and tool configuration into focused components under `src/studio`.
- Enabled shared Testing Library setup and added workflow tests for workspace
  creation/navigation/deletion; file and web ingestion; recovery visibility,
  source filtering/reprocessing/deletion; grounded questions with timed source
  evidence; audio analysis; and YouTube timed transcripts.
- Frontend tests now contain 10 passing tests across five files; the production
  Vite build and API bootJar asset bundling pass. Final diff verification also
  passes.

## OpenSearch integration testing (2026-09-08)

- Step 1 completed: local OpenSearch 3.8.0 runs in Docker on 127.0.0.1:9200
  with persistent data. Configuration is in infra/docker/opensearch.
- test.sh creates an isolated cluster on port 19200, runs the tagged Gradle
  integration suite, and removes test containers/data on exit.
- Real-server tests exposed and fixed delete-by-query's invalid
  refresh=wait_for parameter; the request now uses refresh=true.
- Mapping, bulk indexing, workspace filtering, hybrid/kNN, replacement and
  deletion checks pass. Normal Gradle tests and bootJar also pass.
- Retrieval quality evaluation is the next step; observability and reranking
  have not been started. Changes remain uncommitted.

## Retrieval evaluation (2026-09-08)

- Step 2 implemented: evaluate.sh runs real chunking, local E5 embeddings and
  OpenSearch against 16 synthetic documents and 24 labelled questions.
- 54 hybrid configurations and 9 BM25 configurations run on 12 tuning
  questions; baseline and tuning winner are validated on 12 held-out questions.
- Baseline held-out hybrid Hit@1/3/8: 75%, 91.7%, 100%; BM25: 58.3%, 91.7%, 91.7%.
  Candidate count 8 won tuning but did not improve held-out hit rates. Defaults
  remain unchanged. Detailed findings are in docs/evaluations/retrieval-baseline.md.
- Made heading weight configurable (default 2). Fixed bulk NDJSON UTF-8 encoding
  exposed by the German fixture, and added a real-server multilingual check.
- Step 3 (search observability) is next. Nothing has been committed.

## For-test.txt live test (2026-09-09)

- User steered ongoing observability work to a real-file workspace/answer test.
  Observability is partially implemented (telemetry and actuator dependency);
  health/security checks and tests are NOT complete.
- Found /Users/andriibanul/For-test.txt (Sherlock Holmes anthology).
- Created a separate ai_workspace_document_test PostgreSQL database in existing
  riverbank_db container, and launched API on localhost:8080 using Java 25.
- Registered dedicated test account; credentials and runtime config are stored
  with mode 0600 under ignored data/exports/sherlock-test. Do not print secrets.
- Created workspace 70ef2b15-3fcc-4fea-9392-e37a6ae7fddb; uploaded through real API:
  source b2faa108-93cc-46e0-aa3a-5cf8b5904f8b, 578271 chars, 290 chunks.
- Prepared 12 questions and checked real hybrid retrieval. Seven factual cases
  have complete evidence in top eight; one has only surname; two miss.
- Answer endpoint returns 502 because Gemini key is absent. Asked user for
  local runtime config path; waiting. Do not claim generated answers passed.
- Results: data/exports/sherlock-test/report.md, questions.json,
  retrieved-chunks.json and answer-attempt.json.
- Build succeeded using --no-daemon -Dhttp.proxyHost= -Dhttps.proxyHost= because
  an old Gradle daemon pointed to unavailable proxy port 9091.

## Live answer evaluation completed (2026-09-09)

- User provided Gemini key in /Users/andriibanul/gem-api.txt. Never print it.
  Restarted the owned API process with the key in its environment.
- All 12 real answer requests returned 200. Ten factual cases: seven complete,
  one partial (Norton without Godfrey), two misses (encyclopedia copying and
  fifty-guinea fee). Both deliberately unanswerable cases correctly abstained.
- All returned source IDs match the previously reviewed retrieval set.
- Updated ignored data/exports/sherlock-test/report.md with actual answers,
  expected answers, manual grading and next experiment suggestions.
- API remains running on localhost:8080 with the test database/account.
- No commits. Earlier observability implementation remains unfinished.

## User's 20-question benchmark (2026-09-09)

- Submitted all 20 questions verbatim to the same workspace and actual API.
- All returned HTTP 200. Manual review: 12 broadly correct (some caveats),
  four incomplete and four failed. Full answers and reference criteria are in
  ignored data/exports/sherlock-test/benchmark-20-report.md.
- Raw API results: benchmark-20-answers.json; all 92 unique retrieved chunks:
  benchmark-20-evidence.json. No retrieval or prompt changes during the run.
- Main failures: story title and address lookup; St. Clair's original motive;
  exhaustive marriage/disguise coverage. No commits.

## Sentence chunks and neighboring context (2026-09-09)

- Implemented configurable five-complete-sentence chunks, chapter/section IDs,
  heading paths and paragraph preservation (including Tika plain-text extraction).
- No sentence character limit. Oversized embedding inputs use token-safe inference
  windows, then average and normalize to one vector per stored chunk.
- Retrieval selects five matches and expands each by one adjacent chunk on either
  side within the same workspace/source/section, deduplicated (at most 15).
- Existing sources require reingestion to gain structure metadata. Legacy sources
  still search but do not receive neighboring context.
- Full test suite and bootJar pass, including the final paragraph-preservation
  regression test. Real OpenSearch neighbor-isolation tests pass.
- Updated synthetic evaluation passes: held-out hybrid Hit@1/3/5 =
  50%/91.7%/100%, expanded-context hit = 100%. Top-one performance is lower than
  the old character-chunk baseline (75%); do not claim universal improvement.
- All changes remain uncommitted. Earlier observability work remains incomplete.

- Corrected live book workspace: 96b4de1c-24e4-4fb0-b14d-9dc9308fb87b;
  1,397 chunks across 19 detected sections. Earlier temporary comparison
  workspace fec4baae-2d6d-4bb8-aad2-aaf160fb0eed had flattened paragraphs and is
  obsolete. Original benchmark workspace remains unchanged.
- Live oversized-sentence test: 7,507 characters preserved exactly in one chunk,
  with one 384-dimensional vector. Separate test workspace
  49647080-9a5b-4ec6-8223-d8c16ac922ba.
- Repeated all 20 real book questions: HTTP 200 throughout; manual review 7
  broadly correct, 5 incomplete, 8 failed, versus earlier 12/4/4. Results in
  ignored data/exports/sherlock-sentence-test/benchmark-20-report.md. Do not claim
  retrieval quality improved overall; distant resolutions and broad coverage
  remain weak. No additional tuning performed beyond the requested design.

## Twelve matches plus following chunk (2026-09-09)

- User requested 12 seed matches and only the next chunk, replacing five seeds
  with both neighbors. Retrieval now returns up to 24 deduplicated evidence
  chunks, preserving workspace/source/section restrictions. Chunking, vectors,
  ranking settings and the indexed book are unchanged.
- Full Gradle tests and bootJar pass. Five real OpenSearch tests pass, including
  following-only expansion, isolation, overlap deduplication and 12-to-24
  expansion. API restarted with the new build.
- Benchmark results are saved separately under ignored
  data/exports/sherlock-twelve-next-test; earlier comparison is preserved.
- No commit or push. Earlier observability work remains incomplete.

## Vector-only retrieval experiment (2026-09-09)

- Added `KNOWLEDGE_SEARCH_MODE=VECTOR` and a repository vector-only path. The
  mode embeds the question, runs only OpenSearch kNN with the workspace filter,
  takes the top 12 results from 32 candidates, then applies the existing
  following-chunk expansion. `HYBRID` remains the default.
- Unit tests, full Gradle tests, bootJar, and real OpenSearch integration tests
  pass. The vector-only API benchmark completed all 20 questions with HTTP 200.
- Manual review of the same book benchmark: 14 broadly correct, 5 incomplete,
  1 failed. Hybrid was 12 broadly correct, 5 incomplete, 3 failed. This is a
  single manually reviewed run, not a quality guarantee. Vector-only improved
  exact retrieval cases such as Holmes's address, Wilson's motive, and the
  orange pips, but still missed Julia Stoner's resolution and broad exhaustive
  coverage. Results are in ignored data/exports/sherlock-twelve-next-test/
  benchmark-vector-20-answers.json.
- The local API is currently running with `KNOWLEDGE_SEARCH_MODE=VECTOR` for
  inspection. No commit or push was performed.
- Completed all 20 real questions: HTTP 200 throughout; manual review 12 broadly
  correct, 5 incomplete, 3 failed (previous 7/5/8). Recovered carbuncle discovery,
  St. Clair identity and both comparison subjects. Still misses address and two
  motives; exhaustive story coverage remains incomplete. Report and retrieved
  evidence saved in the new benchmark directory. Maximum evidence count is 24.

## 768-dimensional embedding migration (2026-09-10)

- Replaced the default `intfloat/multilingual-e5-small` model with the pinned
  `intfloat/multilingual-e5-base` ONNX model and changed the configured vector
  dimension from 384 to 768.
- Moved current storage to `knowledge-items-v3`; OpenSearch vector dimensions
  cannot be changed in place. The old v2 index remains available only for local
  comparison.
- Full Gradle tests and `:api:bootJar` pass. The isolated real OpenSearch suite
  passes against the v3 index. A real v3 record was verified to contain 768
  numbers and the expected model identifier.
- Re-ingested the 580,876-character `For-test.txt` source into workspace
  `57b7463c-7e07-4850-b6c4-040bdbba2ed9`: 1,397 chunks across the same structural
  chunking scheme. The API used roughly 2.8 GB RSS during ingestion.
- Repeated the 20-question vector-only benchmark with unchanged retrieval and
  answer settings. All requests returned HTTP 200 in 110.57 seconds. Manual
  review: 16 correct, 3 incomplete, and 1 failed, compared with 14/4/2 for the
  final 384-dimensional rank-preserving run. Julia Stoner improved from a miss
  to a partial answer and the disguise answer expanded from one case to four.
  The exact `221B, Baker Street` lookup still failed, and exhaustive questions
  remain incomplete.
- Raw results are ignored runtime artifacts under
  `data/exports/sherlock-768-test/benchmark-20-answers.json`. The implementation
  and documentation remain uncommitted until Andrii asks for a commit.

## Full-context answer instruction (2026-09-10)

- Strengthened the Gemini knowledge-answer prompt after the Julia Stoner
  benchmark retrieved the resolution but omitted it from the generated answer.
- The model must review every supplied chunk before deciding, combine evidence
  from later and nonadjacent chunks, resolve supported implicit references, and
  include an available outcome or explanation for what/why/how questions.
- Added a focused request-body test; `:knowledge:test` and `:api:bootJar` pass.
- Restarted the vector-only local API and repeated the Julia Stoner question
  against exactly the same 21 chunks. The answer now combines her death before
  the wedding with the later evidence that she was a victim of the speckled-band
  snake. The result is stored in the ignored
  `data/exports/sherlock-768-test/question-9-after-prompt.json` artifact.
- Changes remain uncommitted.

## Full 20-question rerun after answer prompt change (2026-09-10)

- Repeated all 20 questions against the same 768-dimensional vector-only index.
  Every question returned exactly the same ordered chunk IDs as the preceding
  run, isolating the Gemini prompt as the changed variable.
- All requests returned HTTP 200 in 136.98 seconds. Manual review: 17 correct,
  3 incomplete, and 0 failed, compared with 16/3/1 before the prompt change.
- Question 8 improved from abstention to `Baker Street` but still lacks `221B`
  because chunk 709 was not retrieved. Question 9 now combines Julia Stoner's
  death with the later snake resolution. Questions 18 and 20 remain incomplete
  because retrieval lacks exhaustive cross-story coverage; question 20 listed
  only two cases on this run, illustrating answer-model variability.
- Raw results and the report are ignored artifacts under
  `data/exports/sherlock-768-test/benchmark-20-answers-after-prompt.json` and
  `benchmark-20-report-after-prompt.md`. No commit was made.

## Query expansion and reranker evaluation (2026-09-10)

- Added an optional local ONNX cross-encoder reranker and tested it with a
  contextual-embedding re-ingestion. The reranker worked technically, but the
  real 20-question run regressed to approximately 13 correct, 5 incomplete,
  and 2 failed, while increasing ingestion memory to roughly 4.8 GB. It is
  therefore disabled by default (`KNOWLEDGE_RERANKING_ENABLED=false`).
- Added Gemini query expansion before retrieval. The answer model proposes up
  to two concrete search formulations; the service embeds and searches the
  original plus expanded queries, interleaves deduplicated vector/BM25 hits,
  and keeps the existing answer prompt and fallback behavior.
- Re-ran all 20 questions against the unchanged 768-dimensional workspace with
  reranking disabled and query expansion enabled. All requests returned HTTP
  200 in 183.09 seconds (9.15 seconds average). Manual review is approximately
  18 correct, 2 incomplete, and 0 failed. Query 8 now returns the exact
  `221B, Baker Street` address; queries 6, 9, 10, 12, and 16 also include the
  previously missing resolution or causal detail. Exhaustive aggregation in
  queries 18 and 20 remains incomplete, and query 14 is correct but less
  explicit about the discovery sequence.
- Raw results are ignored under
  `data/exports/sherlock-768-test/benchmark-20-answers-query-expansion.json`.
  The implementation and documentation remain uncommitted until Andrii asks
  for a commit.

## Legacy Office and PDF OCR (2026-09-14)

- Enabled DOC, XLS, PPT content types, UI selection, documentation, and binary-format extraction tests.
- Documents renders textless PDF pages and delegates transcription to images via ImageOcrProvider.
- GeminiOcrClient uses the existing Gemini key with a dedicated transcription prompt and rejects truncated/invalid responses.
- PDF OCR preserves native text pages, merges OCR in page order, and retains page numbers in chunks. Pages with partial text layers are not OCRed.
- OCR limits: 50 pages/document, 150 DPI, 4 million pixels/page, maximum rendered side 4096 pixels; standard extraction character/block limits apply.
- OCR confidence/bounds are optional in the contract; the current Gemini adapter returns null for both.
- Tests cover mixed PDF flow, disabled OCR, page/text limits, provider errors, response validation, and blank OCR output. No live Gemini OCR quality benchmark was run.
- Application requires restart to load these changes. Changes remain uncommitted.

## Timed audio and video transcripts (2026-09-15)

- Added a provider-neutral shared transcript segment with millisecond start/end,
  optional speaker, and text.
- Whisper requests detailed JSON with word timestamps and maps returned segments;
  the default faster-whisper deployment does not provide speaker diarization.
- Gemini video understanding now requests structured JSON containing a separate
  visual summary, full spoken transcript, language, and timed segments.
- Direct API responses expose segments, and workspace knowledge stores readable
  timestamped transcript lines so questions can retrieve spoken details.
- The web media-analysis result renders visual summaries and timed transcripts.
- Full Gradle tests, `:api:bootJar`, web tests, web build, and `git diff --check`
  pass. No live provider call was run after this contract change. Changes remain
  uncommitted; YouTube URL ingestion is described in the next section.

## Public YouTube URL ingestion (2026-09-15)

- Added `POST /api/v1/videos/youtube` with workspace ID and one YouTube URL.
- Accepts HTTPS watch, `youtu.be`, Shorts, embed, and live URLs with a valid
  11-character video ID; canonicalizes all forms and rejects playlists alone,
  non-YouTube hosts, credentials, custom ports, and non-HTTPS URLs.
- Uses Gemini's direct public-YouTube input rather than downloading source media.
  Stores only structured visual/transcript knowledge with a generated source ID,
  canonical source URL, extraction time, and parser version.
- Added the YouTube form to the web media-analysis view and documented the API.
- Full Gradle tests, `:api:bootJar`, web tests, and web production build pass.
  API integration covers ownership, provider output mapping, canonical URL/source
  metadata, and knowledge indexing. A live Gemini call was not run because
  `GEMINI_API_KEY` is unavailable in the current shell.
- The endpoint is synchronous and uses the shared HTTP read timeout. The Gemini
  direct-YouTube feature is preview-only and accepts public videos, not private or
  unlisted videos. Changes remain uncommitted.

## Searchable audio transcript chunks (2026-09-15)

- Audio workspace ingestion now indexes each non-empty timed transcript segment
  as a separate OpenSearch knowledge item instead of flattening the transcript
  into one item. Providers without segments fall back to one untimed chunk.
- Audio chunks use the workspace file ID as the stable source ID and retain chunk
  sequence, transcript section, start/end milliseconds, and optional speaker.
  Re-ingestion replaces previous chunks for the same source.
- OpenSearch mapping and serialization now support media timing and speaker
  fields. Retrieval searches speaker labels, embeddings include speaker context,
  and answer sources/context expose the timing and speaker metadata.
- The web answer evidence panel renders audio/video time ranges and speaker labels.
  Existing indexed audio must be re-ingested to gain this representation.
- Focused tests, the full Gradle suite, `:api:bootJar`, frontend tests/build, and
  the isolated real-OpenSearch integration suite pass. Changes remain uncommitted.

## Knowledge responsibility split (2026-09-15)

- `KnowledgeService` remains the stable facade used by API and media modules but
  now delegates indexing, retrieval/reranking, answer generation, and citation
  formatting to focused services.
- The OpenSearch `KnowledgeRepository` implementation remains API-compatible and
  now delegates query/read operations and mutation/write operations separately;
  index lifecycle, mappings, transport errors, and hit deserialization are shared
  through an internal store collaborator.
- Existing retrieval ordering, neighbor expansion, embedding behavior, source
  grouping, timestamps, speaker labels, and citation snippets are unchanged.
- The focused knowledge tests and a clean full Gradle test plus `:api:bootJar`
  build pass. Docker Desktop did not expose a ready engine during verification,
  so the isolated real-OpenSearch integration suite still needs to be rerun.
- Changes remain uncommitted.

## Composed knowledge item metadata (2026-09-15)

- Replaced the 25-component `KnowledgeItem` record with a seven-component
  aggregate containing `KnowledgeItemSource`, `KnowledgeChunkMetadata`, and
  `KnowledgeEmbeddingMetadata` value objects.
- Indexing and OpenSearch serialization/deserialization now construct and consume
  the composed domain model. Temporary flattened read accessors preserve existing
  repository consumers while allowing incremental migration.
- The OpenSearch document schema remains flat and field-compatible; existing
  indices do not require migration or re-ingestion for this change.
- Embedding vectors are defensively copied and exposed as immutable lists.
- Tests construct composed metadata directly, and dedicated model tests cover the
  compatibility view and embedding immutability. The focused knowledge suite and
  clean full Gradle test plus `:api:bootJar` build pass. Docker's Linux engine is
  unavailable, so the isolated real-OpenSearch integration suite was not rerun.
  Changes remain uncommitted.

## Unified media source lifecycle (2026-09-15)

- All HTTP ingestion paths for documents, audio, images, uploaded videos, web
  pages, and YouTube now enter through `OrchestratorService` and share
  `SourceLifecycleCoordinator` transitions.
- Uploaded and URL-backed inputs are registered before processing. URL sources
  extend `workspace_files` with nullable storage/checksum fields and `source_url`;
  `WEB_PAGE` and `YOUTUBE` source types were added in Flyway migration `V6`.
- Every indexed item now uses the stable workspace source ID. Reprocessing uses
  replacement indexing for both chunked and single-item sources, preventing
  duplicate image/video/YouTube knowledge.
- `GET`, `DELETE`, and asynchronous `POST .../reprocess` source endpoints are
  available under `/api/v1/workspaces/{workspaceId}/sources`; existing `/files`
  paths remain compatible. Deletion removes indexed knowledge for every media
  type before removing URL metadata or stored bytes.
- Focused workspace, orchestrator, knowledge, and API tests cover URL source
  creation, lifecycle transition ordering, failure handling, YouTube reprocessing,
  source deletion, and protection against deletion during processing. The full
  Gradle test suite, `:api:bootJar`, frontend tests/build, OpenAPI YAML parsing,
  and `git diff --check` pass. Docker's Linux engine remains unavailable, so the
  isolated real-OpenSearch integration suite was not rerun. Changes remain
  uncommitted.

## HTTP security baseline (2026-09-15)

- HTTP Basic authentication is explicitly stateless, uses configurable BCrypt
  strength 12 for new passwords, and returns consistent JSON 401/403 responses.
- Registration validates email structure and enforces passwords of at least 12
  characters and at most 72 UTF-8 bytes, avoiding silent BCrypt truncation.
- A configurable process-local fixed-window limiter protects API traffic and has
  a tighter registration limit. Denials return JSON 429 responses and
  `Retry-After`; multi-node deployment still requires a shared edge or distributed
  limiter.
- The `SECURITY_AUDIT` logger records sanitized authentication failures,
  authorization-related resource rejection, mutations, and rate-limit denials.
  Actor and direct-client identifiers are hashed and sensitive payloads are not
  logged.
- Optional HTTPS enforcement is available through `SECURITY_REQUIRE_HTTPS` once
  production proxy forwarding is configured. Security integration tests cover
  stateless unauthorized responses, ownership boundaries, password policy, and
  rate limiting.
- A clean full Gradle test suite and `:api:bootJar`, frontend tests/build, OpenAPI
  YAML parsing, and `git diff --check` pass. Changes remain uncommitted.
