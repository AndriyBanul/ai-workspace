# AI Workspace End-to-End Test Specification

## 1. Purpose

This document defines how to verify AI Workspace as a complete product, from the
React UI and HTTP API through PostgreSQL, local file storage, OpenSearch, local
embedding inference, Whisper, Piper, Gemini, Hugging Face FLUX, and Google Veo.

The specification covers every current application module and user-facing
feature:

- `api`: HTTP routing, JSON errors, static UI, OpenAPI, security filters;
- `users`: registration, authentication, password policy, identity;
- `workspaces`: workspace ownership, source metadata, stored-file lifecycle;
- `documents`: TXT, PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, PDF OCR, web pages;
- `audio`: transcription, timed segments, searchable chunks, speech synthesis;
- `images`: image understanding, OCR dependency, image generation;
- `videos`: uploaded-video understanding, spoken transcript, YouTube ingestion,
  video generation;
- `knowledge`: indexing, embeddings, retrieval, citations, grounded answers;
- `orchestrator`: multimodal background jobs and source lifecycle coordination;
- source recovery: retries, leases, dead letters, reconciliation, idempotency;
- `web`: login, workspaces, Library, Ask, Studio, Activity, and responsive UI.

This is a release-verification specification, not a replacement for unit or
module integration tests. Record every executed test, actual result, evidence,
and deviation in the report template in section 20.

### 1.1 Instructions for the executing AI agent

Treat this document as a test plan, not as permission to change application code,
commit, push, reset a worktree, delete existing data, or spend an unbounded amount
on providers. Before testing, read `AGENTS.md`,
`DEVELOPER_CODE_PREFERENCES.md`, `docs/agent-context/README.md`, and
`docs/agent-context/handoff.md`; inspect the current routes/configuration if
they differ from examples here. Record the exact commit plus any uncommitted
diff/untracked files. Do not clean or overwrite the user's worktree to satisfy a
release precondition. For a formal release result, test an immutable commit or
explicitly approved source snapshot; otherwise label the report a working-tree
regression run.

Execute in this order: preflight and fixtures, profile A, profile B API and
storage, profile B browser, profile C in a **separate disposable environment**,
then cleanup and report. A failing case does not justify stopping unrelated
safe cases; record the failure, isolate its impact, and continue. Never infer
success from a unit mock or an HTTP 200 alone. Capture the actual result and
evidence for every test ID. Use `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN`:

- `PASS`: all stated assertions hold in the specified profile;
- `FAIL`: the feature was exercised and a required assertion did not hold;
- `BLOCKED`: a named prerequisite (for example a public fixture, provider key,
  Docker, or approved cost cap) was unavailable; describe the missing item;
- `NOT RUN`: the case was intentionally omitted; give the reason and owner.

Do not call a blocked or partial live suite a full E2E pass. If test data or
credentials would be exposed in output, redact them before preserving evidence.
At the end, return the report path, pass/fail/blocked counts, highest-severity
defects, whether cleanup succeeded, and the release recommendation.

For each case, save a small evidence bundle: sanitized request method/path and
fixture hash, HTTP status/headers/body (or browser screenshot), source/job IDs,
relevant source/recovery/manifest/index snapshot, timestamps, and log lines
filtered by `X-Request-ID`. Separate **observed** data from expected data; do
not put Basic headers, provider tokens, full sensitive documents, or raw
private URLs in shared artifacts. Use bounded polling with a recorded deadline
for asynchronous work and note the final observed state if it times out.

## 2. Test profiles

Use the profile appropriate to the change. A production release candidate must
pass all three profiles in an isolated environment.

| Profile | Purpose | External cost | Destructive |
| --- | --- | --- | --- |
| A. Automated regression | Deterministic Java and React suites, type/boundary checks, build, real disposable OpenSearch adapter checks | None except first model download for retrieval evaluation | The disposable OpenSearch test script removes its own test volume |
| B. Live end-to-end certification | Browser and API tests against real PostgreSQL, OpenSearch, AI providers, Whisper, and Piper | Gemini, Hugging Face, and Veo usage | Only test workspaces |
| C. Recovery and security fault testing | Dependency outages, retries, dead letters, leases, reconciliation, rate limiting, HTTPS behavior | Low to moderate | Yes; isolated environment required |

Never run profile C against a shared development, staging, or production data
set. Use a dedicated database, OpenSearch cluster/index, storage root, test
accounts, and test provider project where possible.

## 3. Release entry criteria

Before starting profile B or C:

1. The intended commit/source snapshot and `git status --short` are recorded.
   A clean tree is required for a release certification, not for a diagnostic
   working-tree run; never reset someone else's changes.
2. Java 25, Node.js, npm, PostgreSQL, Docker, `curl`, and `jq` are available.
3. The test host can reach Gemini, Hugging Face, and the public test URLs.
4. Provider credentials are supplied through environment variables and are not
   written into scripts, reports, shell history, screenshots, or Git.
5. PostgreSQL and file storage contain no data that must be preserved.
6. OpenSearch is bound to localhost or otherwise access-controlled.
7. A tester-controlled public HTML page and public YouTube video are available.
8. Test fixtures satisfy the manifest in section 5.
9. The tester has agreed on a maximum provider spend for generation tests. If
   not, mark paid generation tests `BLOCKED` and run the remaining safe cases.
10. Existing listeners, containers, databases, and storage roots have been
    identified. Do not take over or stop an instance unless it belongs to this
    run. Use unique names/ports or ask for a dedicated environment.

## 4. Environment setup

### 4.1 Isolated configuration

Use `.env.example` as a template and supply values as **process environment
variables** to the API and its dependencies. Spring Boot does not automatically
load a repository `.env` file. A local ignored `.env` may be used by a trusted
shell/launcher, but verify the values are actually exported before booting. At
minimum, configure:

```text
POSTGRES_DB=ai_workspace_e2e
POSTGRES_USER=<dedicated-test-user>
POSTGRES_PASSWORD=<local-secret>
AI_WORKSPACE_STORAGE_ROOT=<absolute-path-to-disposable-e2e-storage>
OPENSEARCH_URL=http://127.0.0.1:9200
GEMINI_API_KEY=<test-project-key>
HUGGING_FACE_API_TOKEN=<test-token>
```

For full coverage, keep these defaults unless the test explicitly changes them:

```text
DOCUMENT_OCR_ENABLED=true
KNOWLEDGE_EMBEDDINGS_ENABLED=true
KNOWLEDGE_EMBEDDING_DIMENSIONS=768
KNOWLEDGE_SEARCH_MODE=HYBRID
KNOWLEDGE_QUERY_EXPANSION_ENABLED=true
KNOWLEDGE_RERANKING_ENABLED=false
SOURCE_RECOVERY_ENABLED=true
SECURITY_REQUIRE_HTTPS=false
```

Use a unique database and storage root per run. The application uses the fixed
OpenSearch index name `knowledge-items-v3`, so use a dedicated OpenSearch cluster
for destructive testing. Record host/port, database name, and storage root in
the report; never include secrets. Keep the quota/rate limits high enough for
the normal suite and lower them only in the isolated security cases.

### 4.2 Start dependencies

Start PostgreSQL using the host's normal service mechanism and create the empty
test database. Flyway must create and validate the schema when the API starts.

From the repository root, start OpenSearch **only if the named Compose project
and port are free or already belong to this test run**:

```bash
docker compose -f infra/docker/opensearch/compose.yml up -d --wait
```

Start Whisper and Piper for audio coverage:

```bash
docker compose -f infra/docker/whisper/compose.yml up -d
docker compose -f infra/docker/piper/compose.yml up -d
```

Verify dependencies before starting the API:

```bash
curl --fail http://127.0.0.1:9200
curl --fail -X POST "http://127.0.0.1:9000/asr?task=transcribe&output=json" \
  -F "audio_file=@<fixture-root>/speech.wav"
```

Piper uses the Wyoming protocol rather than HTTP. Its application-level check is
the text-to-speech test in section 10.

### 4.3 Start the application

After profile A, run from the Gradle root with the E2E environment variables
exported in this process:

```bash
cd apps
./gradlew :api:bootRun
```

The first embedding-enabled start may download approximately 1.13 GB of model
and tokenizer files. Do not assess startup performance during that first
download. Wait for Flyway, JPA validation, and the HTTP server to finish
starting.

Verify:

```bash
curl --fail http://localhost:8080/
curl --fail http://localhost:8080/openapi.yaml
```

`GET /actuator/health` requires authentication and can be checked after account
creation.

### 4.4 Command conventions

Examples below assume Bash or Git Bash, `curl`, and `jq`:

```bash
export BASE_URL=http://localhost:8080
export USER_A_EMAIL="e2e-a-$(date +%s)@example.test"
export USER_A_PASSWORD='E2e-Test-A-Password!'
export USER_B_EMAIL="e2e-b-$(date +%s)@example.test"
export USER_B_PASSWORD='E2e-Test-B-Password!'
```

On Windows PowerShell, use `curl.exe` (not the `curl` alias), `jq`, and
`$env:BASE_URL = 'http://localhost:8080'`; use `.\gradlew.bat` from `apps`.
The OpenSearch `test.sh`/`evaluate.sh` scripts require Bash (for example Git
Bash or WSL with Docker access); `npm`, `docker compose`, and `git` can run from
PowerShell. Keep each example in one shell dialect; Bash `$NAME` will not
interpolate in PowerShell. Never use real personal passwords for test accounts.
Capture HTTP status, headers, and response body separately for negative tests;
`curl --fail-with-body` alone is insufficient for asserting a 4xx/5xx code.

## 5. Test fixture manifest

Keep fixtures outside Git if they are large or licensed. Store a manifest with
SHA-256 hashes in the test report so repeated runs use identical inputs.

| Fixture | Required characteristics | Sentinel facts |
| --- | --- | --- |
| `plain.txt` | UTF-8, headings, paragraphs, more than five sentences | Project Atlas launches Friday; owner is Maya Chen; budget is EUR 42,500 |
| `plain-b.txt` | Separate user B workspace only; never ingested by A | Unique non-sensitive marker `PRIVATE-TO-B-406` |
| `structured.docx` | Heading levels, paragraphs, list, and table | Table contains region North and value 17 |
| `legacy.doc` | Real binary Word document | Unique phrase `LEGACY-WORD-E2E` |
| `sheets.xlsx` | At least two sheets and several rows | Sheet `Forecast` contains Q4 value 913 |
| `legacy.xls` | Real binary Excel workbook | Unique phrase `LEGACY-EXCEL-E2E` |
| `slides.pptx` | At least three slides | Slide 3 says launch city is Porto |
| `legacy.ppt` | Real binary PowerPoint | Unique phrase `LEGACY-SLIDES-E2E` |
| `native.pdf` | Selectable text on at least two pages | Page 2 contains approval code `ORBIT-731` |
| `scanned.pdf` | Textless raster page with clear printed text | OCR phrase `SCANNED-NEBULA-204` |
| `empty.txt` | Zero bytes | None |
| `corrupt.pdf` | Invalid PDF bytes with `.pdf` name | None |
| `protected.pdf` | Password-protected PDF | None |
| `unsupported.zip` | Valid ZIP not containing an accepted Office document | None |
| `speech.wav` | 10–30 seconds, clear speech with at least two separated utterances/returned segments | First: “The blue lighthouse meeting starts at nine thirty.” Second: “The copper key is in cabinet seven.” |
| `speech.mp3` | Same or equivalent speech in MP3 | Includes two separately queryable phrases |
| `scene.png` | Clear objects and readable embedded text | Red bicycle, yellow door, text `IMAGE-DELTA-88` |
| `scene.webp` | WebP variant | Distinct object from `scene.png` |
| `speaking.mp4` | Under 20 MB, visible action and two distinct spoken facts | Speaker says `VIDEO-COMET-52` and later names Tuesday; visible green notebook |
| `silent.mp4` | Under 20 MB, meaningful visual scene, no speech | Orange kite over a beach |
| public HTML URL | Tester-controlled public host, versioned article/main content plus navigation noise | Version 1 includes `WEB-AURORA-19` and owner Iris; version 2 changes the owner to Noor; navigation includes an exclusion marker |
| public text URL | `text/plain`, explicit UTF-8 or another known charset | Includes `WEB-TEXT-E2E` |
| public YouTube URL | Tester-owned public video, short, stable, with two distinct spoken facts and visuals | Spoken `YOUTUBE-SATURN-61` and later names Thursday; visible silver cup |

Also prepare over-limit files without committing them:

- image larger than 10 MB;
- video larger than 20 MB;
- audio larger than 25 MB;
- multipart submission totaling 25 MB or more;
- document producing more than 1,000,000 extracted characters.

Do not use `localhost`, private IPs, link-local addresses, or documentation IP
ranges for the web-page success fixture. The web fetcher intentionally blocks
non-public destinations to prevent SSRF.

Before ingestion, prepare a fixture ledger with each sentinel, its expected
source, section/page/sheet/slide or transcript time where known, and questions
that retrieve it. Confirm the speech/video sentinels are actually audible and
the image/video objects visible; do not treat OCR/transcription output as the
ground truth. Keep the two HTML versions under tester control and save their
body hashes so LIFE-02 can prove changed content was re-fetched. The executing
agent may create synthetic local files under the ignored run directory, but
legacy Office/PDF/media fixtures must be valid decodable binaries, not renamed
plain text. Obtain or publish public fixtures only in an approved test account;
if that prerequisite is absent, mark the relevant live cases `BLOCKED`.

## 6. Profile A: automated regression

Run the clean application suite and executable packaging:

```bash
cd apps
./gradlew clean test verifyModuleBoundaries :api:bootJar
```

Acceptance criteria:

- every Gradle module compiles;
- all Java tests pass;
- Flyway migrations apply in context tests;
- Spring wiring and `bootJar` creation pass;
- no test unexpectedly contacts a paid provider.

Run frontend tests and production build:

```bash
cd apps/web
npm ci
npm test
npm run typecheck
npm run build
```

Acceptance criteria:

- API helper tests pass;
- workspace, Library, Ask, Studio, Activity, login, API error handling, and
  routing workflow tests pass;
- the API/routing JSDoc type check passes;
- Vite produces `dist/index.html` and hashed assets without warnings that
  indicate broken imports.

Run real OpenSearch adapter integration tests from the repository root:

```bash
./infra/docker/opensearch/test.sh
```

Acceptance criteria:

- mappings and 768-dimensional vector fields are accepted;
- BM25 and kNN queries both contribute results;
- workspace isolation, source replacement, source deletion, and workspace
  deletion pass against a real server.

Run the retrieval benchmark when chunking, embeddings, retrieval, ranking, or
answer context selection changed:

```bash
./infra/docker/opensearch/evaluate.sh
```

The current permissive expanded-context held-out gate must pass. Attach
`apps/api/build/reports/retrieval/summary.md` and `results.json` to the report.
Do not describe this synthetic benchmark as proof of production answer quality.

Finally run:

```bash
git diff --check
git status --short
```

For a formal release run, verify the tested commit and clean tree again. For a
working-tree run, record any changes produced by testing and distinguish them
from pre-existing changes; do not discard either set. Profile A's OpenSearch
script uses the fixed disposable test project on port 19200 and removes its
test volume on exit, so confirm it is not carrying anyone else's data first.

## 7. Authentication and users

### AUTH-01 Register a valid account

```bash
curl --fail-with-body -sS -X POST "$BASE_URL/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_A_EMAIL\",\"password\":\"$USER_A_PASSWORD\",\"displayName\":\"E2E Owner A\"}" \
  | tee user-a.json
```

Expected:

- HTTP 201;
- response contains a generated `id`, normalized email, display name, and
  timestamps;
- response does not contain a password or password hash.

Repeat for user B.

### AUTH-02 Authenticate

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  "$BASE_URL/api/v1/auth/me"
```

Expected HTTP 200 and the same user ID as AUTH-01. No `Set-Cookie` header should
be returned because authentication is stateless.

### AUTH-03 Reject invalid authentication

Request `/api/v1/workspaces` without credentials and with an incorrect password.

Expected for both:

- HTTP 401;
- JSON error code `AUTHENTICATION_REQUIRED`;
- `Cache-Control: no-store`;
- `WWW-Authenticate` begins with `Basic realm="ai-workspace"`;
- no server session cookie.

### AUTH-04 Validate registration policy

Verify HTTP 400 for:

- missing body;
- blank or malformed email;
- email containing whitespace or multiple `@` characters;
- blank password;
- password shorter than 12 characters;
- password longer than 72 UTF-8 bytes, including a multibyte-character case.

Register the same normalized email twice. The second request must fail without
exposing a password hash or database detail.

### AUTH-05 Verify audit logging

Capture application logs while performing one failed login, one mutation, one
cross-owner access attempt, and one rate-limit denial. `SECURITY_AUDIT` entries
must identify event/outcome/status using hashed fingerprints. Logs must not
contain the raw email, password, Basic header, request body, URL query string,
document text, or provider key.

## 8. Workspaces and ownership

### WS-01 Create and list a workspace

```bash
export WORKSPACE_A=$(curl --fail-with-body -sS \
  -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/workspaces" \
  -H 'Content-Type: application/json' \
  -d '{"name":"E2E multimodal workspace"}' | jq -r .id)
```

Expected:

- creation returns HTTP 201;
- `ownerId` is user A;
- list and get endpoints return the workspace;
- blank workspace name returns HTTP 400 with the standard API error shape.

Create `WORKSPACE_B` as user B and ingest `plain-b.txt` there. Verify its marker
is answerable for B before using it as the negative isolation control for A.

### WS-02 Enforce ownership without disclosure

As user B, attempt to get, list sources for, upload into, query, reprocess, and
delete user A's workspace and sources. Attempt to read user A's async job.

Expected HTTP 404 for each ownership-scoped lookup. The error must not confirm
that user A's resource exists. User A's metadata, files, knowledge, and jobs must
remain unchanged.

### WS-03 Verify source aliases

For a source created later, compare:

- `/workspaces/{workspaceId}/files` and `/sources`;
- `/files/{sourceId}` and `/sources/{sourceId}`;
- reprocess, recovery, and delete paths under both aliases.

The aliases must enforce identical ownership and return equivalent data.

## 9. Documents module

For every successful direct upload, verify all of the following:

1. HTTP 200 and a nonblank `sourceId`.
2. `detectedContentType`, extraction counts, `extractedAt`, and
   `parserVersion` are present and plausible.
3. The source appears as `DOCUMENT` and `PROCESSED`.
4. `storageKey` belongs to the test workspace, `checksumSha256` has 64 hex
   characters, and the stored bytes exist beneath the disposable storage root.
5. Recovery state is `PROCESS` / `COMPLETED`, attempt count is at least 1, and a
   future reconciliation time exists.
6. Workspace knowledge contains the sentinel text.
7. A question about the sentinel returns a grounded answer citing this source.

Base command:

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/documents/text" \
  -F "workspaceId=$WORKSPACE_A" \
  -F "file=@<fixture-root>/plain.txt"
```

### DOC-01 Plain text and chunking

Upload `plain.txt`. Verify ordered chunks conceptually by asking separately for
the launch date, owner, and budget. Citations must have the same source ID and
meaningful snippets. Repeated sentences must not be cut in the middle solely to
meet a character boundary.

### DOC-02 Modern Office formats

Upload `structured.docx`, `sheets.xlsx`, and `slides.pptx` independently.

Expected:

- DOCX headings, paragraphs, lists, and table rows remain readable;
- XLSX evidence identifies `sheetName=Forecast` where applicable;
- PPTX evidence identifies `slideNumber=3` for the Porto fact.

### DOC-03 Legacy Office formats

Upload real binary `legacy.doc`, `legacy.xls`, and `legacy.ppt` files. Each must
be detected from content and become searchable. Renaming plain text to `.doc`
does not satisfy this test.

### DOC-04 Native PDF

Upload `native.pdf`. Ask for `ORBIT-731`. The answer source should identify page
2. Native selectable text must not be replaced by OCR output.

### DOC-05 Scanned PDF OCR

With OCR enabled and `GEMINI_API_KEY` configured, upload `scanned.pdf`.

Expected:

- `SCANNED-NEBULA-204` is transcribed and searchable;
- page ordering is preserved;
- no invented confidence or bounding coordinates appear when the Gemini adapter
  does not provide them;
- a provider failure fails the source rather than indexing partial OCR.

Repeat once with `DOCUMENT_OCR_ENABLED=false`; a fully textless PDF must return
`NO_EXTRACTABLE_TEXT` and remain visible as a `FAILED` source.

### DOC-06 Document failures

Verify:

| Input | Expected status/code | Source state |
| --- | --- | --- |
| `empty.txt` | 400 `EMPTY_DOCUMENT` | `FAILED`, no knowledge |
| `unsupported.zip` | 415 `UNSUPPORTED_DOCUMENT_FORMAT` | `FAILED`, no knowledge |
| `protected.pdf` | 422 `PASSWORD_PROTECTED_DOCUMENT` | `FAILED`, no knowledge |
| `corrupt.pdf` | 422 `CORRUPT_DOCUMENT` | `FAILED`, no knowledge |
| over extraction limit | 422 `EXTRACTION_LIMIT_EXCEEDED` | `FAILED`, no partial knowledge |
| textless PDF with OCR disabled/blank | 422 `NO_EXTRACTABLE_TEXT` | `FAILED`, no knowledge |

Error JSON must include `timestamp`, numeric `status`, `detail`, request `path`,
and stable `code`, without stack trace or internal cause.

### DOC-07 Web-page ingestion

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/documents/web-page" \
  -H 'Content-Type: application/json' \
  -d "{\"workspaceId\":\"$WORKSPACE_A\",\"url\":\"$PUBLIC_HTML_URL\"}"
```

Expected:

- HTTP 200; source type `WEB_PAGE`, canonical/final public URL retained;
- title and `WEB-AURORA-19` are extracted;
- navigation/form/footer exclusion marker is absent where main/article
  extraction applies;
- headings, paragraphs, lists, and table rows retain readable boundaries;
- `characterCount`, `storedCharacterCount`, and `loggedCharacterCount` agree;
- `truncated=false`;
- content is indexed and answerable with the URL in citations.

Repeat for the public plain-text URL and a known non-UTF-8 page if available.
Verify declared HTTP charset and HTML meta charset handling.

Negative web-page cases:

- blank or malformed URL;
- `file:`, `ftp:`, or another non-HTTP scheme;
- URL containing user info;
- localhost, loopback, private, link-local, multicast, carrier-grade NAT, or
  reserved/documentation address;
- redirect to a non-public destination;
- more than five redirects;
- response larger than 2 MiB;
- non-text response such as an image;
- DNS failure and upstream 5xx.

The request must fail without creating processed knowledge or permitting SSRF.

## 10. Audio module

### AUDIO-01 Timed transcription

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/audio/transcriptions" \
  -F "workspaceId=$WORKSPACE_A" \
  -F "file=@<fixture-root>/speech.wav" | tee audio-response.json
```

Expected:

- HTTP 200, filename, full text, detected language, and nonempty timed segments;
- each segment has nonnegative start/end milliseconds, end is not before start,
  and text is nonblank;
- speaker is absent unless the configured provider performs diarization;
- source is `AUDIO` / `PROCESSED` with completed recovery state;
- each nonblank timed segment is independently searchable in OpenSearch;
- asking separately about the lighthouse meeting and copper key returns the
  relevant **different** chunks with their respective time ranges, rather
  than citing the entire transcript; speaker appears only when supplied by the
  transcription provider.

For the indexed source, compare the count of active-generation OpenSearch
documents with the number of nonblank provider segments (or one fallback
document). Check each indexed `chunkSequence`, `startMilliseconds`,
`endMilliseconds`, and optional `speaker` against the response. This is the
regression guard for the previously implemented audio chunking; a full-text
field containing the transcript is not sufficient.

Repeat with MP3. Providers that return no segments may use one untimed fallback
chunk; record this explicitly rather than failing the run.

Negative cases: empty input, unsupported extension, file over 25 MB, and Whisper
unavailable. Validation failures must not index knowledge. A transient Whisper
outage must produce retryable recovery behavior in profile C.

### AUDIO-02 Text to speech

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/audio/speech" \
  -H 'Content-Type: application/json' \
  -d '{"text":"The AI Workspace end-to-end speech test is complete."}' \
  --output e2e-speech.wav
```

Expected:

- HTTP 200, `Content-Type: audio/wav`, attachment filename;
- output begins with a valid RIFF/WAVE header, has nonzero duration, and is
  intelligible when played;
- blank text and missing body return HTTP 400;
- Piper outage returns a controlled upstream error, not a stack trace.

## 11. Images module

### IMAGE-01 Image understanding

Upload `scene.png` to `/api/v1/images/descriptions`.

Expected:

- HTTP 200 and a description containing the red bicycle, yellow door, and
  `IMAGE-DELTA-88` where legible;
- source type `IMAGE`, processed lifecycle, stored bytes, checksum, and completed
  recovery state;
- image description is present in workspace knowledge and can ground a question;
- citation identifies the image source.

Repeat with JPEG and WebP. Negative cases: empty file, unsupported extension,
file over 10 MB, missing Gemini configuration, and upstream failure.

### IMAGE-02 Image generation

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/images/generations" \
  -H 'Content-Type: application/json' \
  -d '{"description":"A blue ceramic lighthouse on a white desk, product photograph"}' \
  --output generated-image.png
```

Expected:

- HTTP 200, image content type, attachment filename, nonempty decodable image;
- output materially follows the prompt;
- blank description, missing body, and description over 4,000 characters return
  HTTP 400;
- missing/invalid Hugging Face token and provider errors return controlled errors.

Generation does not automatically add output to workspace knowledge. Upload the
generated image through Library if that behavior is desired, then verify normal
image ingestion.

## 12. Videos module

### VIDEO-01 Uploaded video with speech

Upload `speaking.mp4` to `/api/v1/videos/descriptions`.

Expected:

- visual summary mentions the green notebook;
- transcript contains `VIDEO-COMET-52`;
- language and ordered timed segments are returned;
- visual summary and spoken transcript remain distinguishable;
- source type `VIDEO`, processed lifecycle, stored bytes, and knowledge exist;
- a question about the spoken phrase is answered from this source; the API
  response contains timed segments, but current video knowledge is indexed as
  **one combined visual-summary/transcript item**, not as timed segment chunks;
  do not require timestamped answer citations for video yet;
- a visual question is answerable from the visual summary.

Ask separately for `VIDEO-COMET-52`, the later spoken day, and the green
notebook. Confirm speech is not mistaken for an observed visual fact. Compare
the response's timed segments with the actual fixture, and verify exactly one
active knowledge item for this video source.

### VIDEO-02 Silent video

Upload `silent.mp4`. The visual summary must describe the orange kite/beach.
Transcript and segments may be empty without failing processing. Knowledge must
still contain the visual summary.

Negative cases: empty file, unsupported extension, file over 20 MB, missing
Gemini key, malformed provider response, and provider timeout.

### VIDEO-03 Public YouTube ingestion

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/videos/youtube" \
  -H 'Content-Type: application/json' \
  -d "{\"workspaceId\":\"$WORKSPACE_A\",\"url\":\"$PUBLIC_YOUTUBE_URL\"}" \
  | tee youtube-response.json
```

Expected:

- source type `YOUTUBE`, stable source ID, null storage/checksum fields, and
  canonical `https://www.youtube.com/watch?v=<11-character-id>` URL;
- visual summary mentions the silver cup;
- transcript contains `YOUTUBE-SATURN-61` with ordered timed segments;
- derived knowledge is searchable and cited with the YouTube URL; as with
  uploaded video, current indexing stores one combined item, so answer
  citations need not have per-segment time ranges;
- no source video bytes are stored locally.

Ask separately about the first spoken code, later spoken day, and silver cup;
verify against the original video, not only Gemini's returned text. Inspect the
single active source knowledge item and ensure it contains both labelled visual
and spoken portions.

Repeat URL parsing with watch, `youtu.be`, Shorts, embed, and live forms for the
same video ID. They must canonicalize consistently. Reject HTTP, credentials,
custom ports, non-YouTube hosts, playlist-only URLs, missing/invalid video IDs,
blank requests, and videos that the configured provider cannot access. Treat an
unlisted video as valid when its URL is known and the provider can access it;
private or otherwise inaccessible videos must fail with a controlled error.

### VIDEO-04 Video generation

Post a short prompt to `/api/v1/videos/generations` and allow enough time for
Veo polling.

Expected:

- HTTP 200, video content type, attachment filename, playable nonempty video;
- result materially follows the prompt;
- blank/missing/over-4,000-character descriptions return HTTP 400;
- timeout, rejected prompt, missing key, and provider failure are controlled.

Record provider operation duration and cost. Video generation is not added to a
workspace until the user uploads the result.

## 13. Orchestrator module

### ORCH-01 Multimodal background ingestion

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/orchestrator/ingestions" \
  -F "workspaceId=$WORKSPACE_A" \
  -F "document=@<fixture-root>/plain.txt" \
  -F "audio=@<fixture-root>/speech.mp3" \
  -F "image=@<fixture-root>/scene.png" \
  -F "video=@<fixture-root>/speaking.mp4" | tee ingestion.json
```

Expected HTTP 202 with:

- generated `jobId`;
- status `RUNNING` initially, unless all very fast tasks already completed;
- submitted types for documents, audio, images, and videos;
- stable `sourceIds` for each submitted type.

Poll until terminal:

```bash
export JOB_ID=$(jq -r .jobId ingestion.json)
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  "$BASE_URL/api/v1/orchestrator/jobs/$JOB_ID" | jq
```

Repeat the GET approximately every two seconds until the job is terminal or a
recorded deadline expires (normally 30 minutes for real video providers).
Capture at least the initial and terminal responses; do not poll forever or
assume a still-running job is a pass.

Expected final state:

- job `COMPLETED` when all steps succeed;
- every submitted step transitions `PENDING` → `RUNNING` → `COMPLETED` with
  timestamps;
- omitted parts appear as `SKIPPED` and create no source;
- all source IDs match source list and knowledge records;
- each source reaches `PROCESSED` and recovery `COMPLETED`.

Also call `GET /api/v1/orchestrator/jobs?workspaceId=<id>&limit=50`. The new
job must appear in reverse creation order, with the same persisted step data.
The default limit is 50 and the server clamps requested limits to 1–100.

### ORCH-02 Partial failure

Submit one valid image and one invalid/empty named document. Expected final job
status is `PARTIALLY_FAILED`; image completes and is searchable, document step
contains `EMPTY_DOCUMENT` and readable error text, and its source is failed.

Submit only invalid content. Expected terminal status is `FAILED`.

### ORCH-03 Job ownership and restart persistence

- User B must receive 404 for user A's job.
- Restart the API after a terminal job; the owner must still retrieve status,
  steps, errors, and timestamps from PostgreSQL, both by ID and in the recent
  jobs list. User B must receive 404 when listing jobs for user A's workspace.
- Activity UI lookup by job ID must show the same state.

## 14. Source lifecycle, idempotency, and deletion

Run these checks for at least one source of every type: document, audio, image,
uploaded video, web page, and YouTube. Capture the source ID, job ID (including
the job behind a synchronous import), recovery status, and active manifest row
for each. Never compare only filenames: they are not unique identities.

### LIFE-01 Source metadata

List and get sources. Verify unique IDs, source type, original/display name,
status, timestamps, source URL for remote inputs, and storage/checksum metadata
only for uploaded inputs. A source must be created before processing begins.

### LIFE-02 Reprocessing

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/workspaces/$WORKSPACE_A/sources/$SOURCE_ID/reprocess"
```

Expected HTTP 202 and a new job referencing the same source ID. After completion:

- source returns to `PROCESSED`;
- recovery attempt is completed;
- the active index manifest points to one complete generation, with no old or
  unpublished chunks returned by workspace knowledge or answer retrieval;
- changed URL content is reflected for web sources where the remote fixture was
  intentionally versioned;
- attempting concurrent reprocessing or deletion while `PROCESSING` returns
  HTTP 409.

For the versioned HTML fixture, ingest version 1, confirm the old owner fact,
switch the public fixture to version 2, reprocess the same source ID, then
confirm the new owner fact and absence of the old fact from active retrieval.
Record both page hashes and the before/after manifest generation IDs. Repeat a
reprocess for an uploaded source to prove stored original bytes are reused.

### LIFE-03 Source deletion

Delete each source. Expected HTTP 204. Verify source GET returns 404, stored bytes
are removed when present, source-scoped OpenSearch knowledge is gone, and other
sources remain intact. Repeating delete may return 404; it must not damage other
data.

### LIFE-04 Workspace deletion

Create a disposable workspace containing all source types. Delete the workspace.
Verify workspace, sources, stored bytes, jobs/steps governed by cascading rules,
recovery tasks, and OpenSearch knowledge are removed while another owner's
workspace remains intact.

### LIFE-05 Idempotent asynchronous submissions

Submit the same background upload twice with the same `Idempotency-Key` header.
Expect HTTP 202 and the same job and source IDs, with one persisted submission;
compare original and replayed response bodies, not just counts. Reuse the key
for different file bytes, filename, media slot, workspace, or operation and
expect HTTP 409 without new sources. Repeat for source reprocessing with the
same source ID. After restarting the API, the original response must still
replay. An invalid key (more than 128 characters or non-visible ASCII) returns
400. Omit the key once to confirm a new submission is created. Do not test
30-day expiration by altering the application clock; inspect the retention
contract separately. Direct synchronous media endpoints are outside this
contract. Replayed and failed requests still count toward the per-user daily
ingestion quota, so set a sufficient quota for this case.

### LIFE-06 Generation cutover and reconciliation

With an isolated OpenSearch cluster, stage a replacement generation while the
existing one is active. Before publication, workspace knowledge and answer
retrieval must return only the old generation; after completion, only the new
one. In the dedicated E2E PostgreSQL database, inspect
`source_index_manifests(source_id, workspace_id, active_generation,
expected_items)` and count OpenSearch documents filtered by this source ID and
`sourceGeneration=<sourceId>:<active_generation>`. The count must equal
`expected_items`, with no duplicate active chunk IDs. Inactive/staged items
may exist briefly in raw OpenSearch and are acceptable only while invisible to
application readers; reconciliation must prune them. Simulate a partial active
generation by removing one source-scoped chunk, then wait for reconciliation to
schedule reprocessing. Inspect or mutate only this fixture's records. To
observe the pre-publication state deterministically, use a controllable slow
provider/test hook in the disposable environment; if unavailable, record the
real post-publication check and automated staged-generation result separately,
and mark the live cutover assertion `BLOCKED`.

## 15. Knowledge module

### KNOW-01 Aggregated knowledge

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  "$BASE_URL/api/v1/knowledge/workspaces/$WORKSPACE_A" | tee knowledge.json
```

Expected HTTP 200 with populated fields corresponding to ingested documents,
audio, images, and video. No content from user B or another workspace may appear.

### KNOW-02 Grounded answers by modality

Ask the following prewritten questions after all corresponding sources are
`PROCESSED`. If a fixture differs, replace the wording **before** ingestion and
record the expected fact/source in the fixture ledger. At least these 17
positive cases plus the two negative cases are required for a full live run:

| ID | Question to ask | Required answer and primary evidence |
| --- | --- | --- |
| Q01 | When does Project Atlas launch? | Friday; `plain.txt` |
| Q02 | Who owns Project Atlas? | Maya Chen; `plain.txt` |
| Q03 | What is the Atlas budget? | EUR 42,500; `plain.txt` |
| Q04 | What value does the North region have? | 17; `structured.docx` table |
| Q05 | What is the Q4 Forecast value? | 913; `sheets.xlsx`, sheet `Forecast` |
| Q06 | Which city is named on slide 3? | Porto; `slides.pptx`, slide 3 |
| Q07 | What approval code is on PDF page 2? | `ORBIT-731`; `native.pdf`, page 2 |
| Q08 | What code is on the scanned PDF? | `SCANNED-NEBULA-204`; `scanned.pdf` |
| Q09 | When does the blue lighthouse meeting start? | Nine thirty; first `speech.wav` segment/time |
| Q10 | Where is the copper key? | Cabinet seven; later `speech.wav` segment/time |
| Q11 | What is by the yellow door, and what text is visible? | Red bicycle and `IMAGE-DELTA-88`; `scene.png` |
| Q12 | What code and later day are spoken in the uploaded video? | `VIDEO-COMET-52`, Tuesday; `speaking.mp4` transcript |
| Q13 | What object is visibly present in the uploaded video? | Green notebook; `speaking.mp4` visual summary |
| Q14 | What code and later day are spoken in the YouTube video? | `YOUTUBE-SATURN-61`, Thursday; public YouTube transcript/URL |
| Q15 | What object is visible in the YouTube video? | Silver cup; YouTube visual summary/URL |
| Q16 | What code and current owner does the imported article state? | `WEB-AURORA-19` and the active HTML version's owner; public page URL |
| Q17 | Compare the Atlas budget with the Forecast Q4 value; identify their respective sources. | Both values attributed separately to `plain.txt` and `sheets.xlsx`; no invented relation or unit conversion |
| N01 | What is the unpublished Project Atlas profit margin? | State insufficient evidence; no invented number or supporting citation |
| N02 | As user A, ask “What is the private marker in user B's workspace?” | No disclosure of `PRIVATE-TO-B-406` or B's source; insufficient evidence |

Example:

```bash
curl --fail-with-body -sS -u "$USER_A_EMAIL:$USER_A_PASSWORD" \
  -X POST "$BASE_URL/api/v1/knowledge/workspaces/$WORKSPACE_A/answers" \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the Project Atlas budget and who owns it?"}'
```

For every answer, record the raw response and grade three independent stages:
extraction/index (is the fact present in the correct active source item?),
retrieval (is a supporting snippet in `sources`?), and generation (does `answer`
state the fact without contradiction?). A response is `PASS` only if the
required fact is correct and at least one cited snippet from the correct source
supports each material claim. Use `PARTIAL` only in the AI-quality worksheet,
not as an overall test status; a required partially correct answer makes that
question's test `FAIL`. Record unsupported extra claims as failures, even if
the expected fact appears. For N01/N02, require an explicit insufficient-
evidence answer with no false citation.

For each citation, verify `sourceId`, `sourceName`, `sourceUrl` for remote
sources, `snippet`, and applicable `pageNumber`, `slideNumber`, `sheetName`,
`chunkSequence`, `startMilliseconds`, `endMilliseconds`, and `speaker`. Audio
Q09/Q10 must cite distinct timed chunks. Uploaded-video and YouTube citations
currently point to a combined item, so timing is checked in ingestion responses,
not required in answer citations. Record latency, HTTP status, provider/model,
and any source-mismatch or cross-owner leak. Do not require exact generated
wording; evaluate against the fixture ledger and the original media, not another
model's unsupported judgment.

### KNOW-03 Retrieval behavior

- Ask paraphrases and one cross-language question to exercise embeddings.
- Stop or misconfigure query embeddings temporarily and verify BM25 fallback.
- Run once with `KNOWLEDGE_SEARCH_MODE=VECTOR` and compare evidence, without
  changing the production default solely from one anecdotal result.
- Confirm the same question in two workspaces cannot retrieve the other's
  matching sentinel, including after neighbor-context expansion.
- If query expansion is enabled, verify answer correctness and latency; logs must
  not expose prompts or sensitive source content.
- Existing sources indexed under older schemas must be re-ingested before
  expecting current chunk structure and media timing.

### KNOW-04 Validation and provider failures

Blank question, missing body, and question longer than 4,000 characters return
HTTP 400. Missing Gemini answer configuration or provider failure returns a
controlled 502. Retrieval or answer failure must not mutate source knowledge.

### KNOW-05 Saved answer history

Ask two distinguishable questions, reload the app and restart the API, then
call `GET /api/v1/knowledge/workspaces/{workspaceId}/answers?limit=50`. Both
answers and their citations must be restored newest-first, without a duplicate
being created by reload. Requested limits are clamped to 1–100. User B must
receive 404 for user A's history. A failed question must not appear as a saved
answer; distinguish failed generation from a successful insufficient-evidence
answer, which is saved.

## 16. Profile C: recovery, reconciliation, and security faults

For faster dedicated recovery tests, start the API with short but nonzero
values such as:

```text
SOURCE_RECOVERY_INITIAL_BACKOFF=2s
SOURCE_RECOVERY_MAX_BACKOFF=10s
SOURCE_RECOVERY_LEASE_DURATION=10s
SOURCE_RECONCILIATION_INTERVAL=5s
SOURCE_RECOVERY_POLL_INTERVAL=1s
SOURCE_RECOVERY_MAX_ATTEMPTS=3
```

Keep normal rate limits and daily quotas during REC cases to avoid mistaking
429 for a recovery defect. For SEC-01 and SEC-04, restart the **disposable** API
with low limits and fresh users, for example:

```text
SECURITY_RATE_LIMIT_REQUESTS_PER_MINUTE=5
SECURITY_REGISTRATION_RATE_LIMIT_REQUESTS_PER_MINUTE=2
USER_INGESTIONS_PER_DAY=2
USER_ANSWERS_PER_DAY=2
USER_GENERATIONS_PER_DAY=1
```

Do not use these values for production sizing. Each fault must specify its
injection point, expected state before recovery, maximum wait/deadline, and
evidence. Only stop processes/containers that this run started. Preserve
source- and workspace-filtered DB/index snapshots before and after each fault.

### REC-01 Processing retry after OpenSearch outage

1. Stop the isolated OpenSearch cluster.
2. Upload a valid document.
3. Confirm the request fails cleanly, source becomes `FAILED`, and recovery state
   is `SCHEDULED` with an error and future `nextAttemptAt`.
4. Restart OpenSearch before attempts are exhausted.
5. Wait for recovery.

Expected: attempt count increases, source becomes `PROCESSED`, recovery becomes
`COMPLETED`, and exactly one complete source chunk set exists.

### REC-02 Deletion retry

1. Process a source successfully.
2. Stop OpenSearch.
3. Delete the source.
4. Verify deletion fails and the stored source remains available.
5. Restart OpenSearch and wait for retry.

Expected: knowledge is removed first, then stored bytes/source metadata are
removed; retrying converges without error if one side was already deleted.

### REC-03 Dead letter

Keep the dependency unavailable through `SOURCE_RECOVERY_MAX_ATTEMPTS`.
Expected recovery status `DEAD_LETTER`, null next attempt/lease, bounded error,
and no tight retry loop. Manual reprocessing after restoring the dependency must
start a fresh attempt sequence and be able to complete.

### REC-04 Expired lease after process termination

1. Use a slow media operation and wait until recovery status is `RUNNING`.
2. Terminate the API process without graceful completion.
3. Restart with the same database, storage, and OpenSearch.
4. Wait past the configured lease.

Expected: the expired task is reclaimed once, attempt count increases, and
processing converges without duplicate knowledge.

### REC-05 Missing indexed knowledge reconciliation

In the isolated OpenSearch cluster, delete only the test source's documents from
`knowledge-items-v3` using a source-ID-filtered delete-by-query. Do not delete the
whole index. Keep source metadata and bytes intact.

Expected after reconciliation: task is scheduled for repair, source is
reprocessed, and its complete knowledge set reappears exactly once.

### REC-06 Missing stored content reconciliation

For one disposable uploaded source, resolve its exact `storageKey` beneath the
dedicated E2E storage root and remove only that file. Verify the resolved path is
inside the disposable root before removal.

Expected after reconciliation: recovery becomes `DEAD_LETTER` with
`SOURCE_CONTENT_MISSING`; the application does not invent or silently recreate
source bytes. URL-backed sources are not subject to local byte existence checks.

### REC-07 Recovery ownership

Only the source owner may call the recovery endpoint. Another user receives 404.
After a source is deleted, its owner-facing source/recovery endpoint also returns
404.

### REC-08 Renewable lease and fenced stale worker

In a disposable two-API-instance setup sharing the same E2E PostgreSQL,
OpenSearch, and storage, use a slow controlled provider response longer than
one lease period. Verify the active worker renews its lease and neither instance
starts a duplicate active attempt. Then pause a claimed worker long enough for
another instance to reclaim its expired lease, and resume the stale worker.
The stale worker must not
publish a generation, mark a job complete, or overwrite the newer task state.
Require one current active manifest and complete item count. Use a documented
test hook or controllable provider fixture; if deterministic pause/resume is
unavailable, mark the live race test `BLOCKED` and attach the automated fencing
test result—do not claim a live pass from it.

### REC-09 Queued submission durability and worker saturation

On a disposable API, set a small `INGESTION_WORKER_THREADS` and
`INGESTION_QUEUE_CAPACITY`, submit more slow test sources than the queue can
hold, and record every HTTP 202 response and source/job ID. Stop only the API
instance owned by this run while at least one step is still `PENDING`, then
restart against the same DB, index, and storage. Every accepted source must
eventually complete or expose a bounded `FAILED`/`DEAD_LETTER` outcome;
none may disappear or stay silently pending. Check that after-commit dispatch
and recovery create no duplicate active knowledge. If a controlled slow
provider is unavailable, run the automated queue/restart tests and mark the
live variant `BLOCKED`.

### SEC-01 Rate limiting

With low dedicated limits, send requests from one client address until the limit
is exceeded. Expected:

- final response HTTP 429;
- code `RATE_LIMIT_EXCEEDED`;
- positive integer `Retry-After`;
- registration and general API buckets are independent;
- requests succeed again after the fixed window expires.

The current limiter is process-local. A multi-instance deployment requires an
additional gateway/distributed-limiter test and must not claim global enforcement
from this case.

### SEC-02 HTTPS enforcement

Behind a test reverse proxy that supplies trusted forwarded-scheme headers, set
`SECURITY_REQUIRE_HTTPS=true`.

Expected: HTTP requests redirect to HTTPS and HTTPS requests succeed. Verify no
redirect loop. Do not enable this test against direct local HTTP without the
proxy configuration needed to represent the original scheme.

### SEC-03 Standard error contract

Across representative 400, 401, 404, 409, 415, 422, 429, and 502 cases, verify
the JSON error contract is consistent, contains no stack trace or secret, and
uses a stable code where the domain defines one.

### SEC-04 Quotas, correlation, and metrics

With low test-only daily quota values, exhaust each user's ingestion, answer,
and generation allowance. Expect HTTP 429, code `USER_QUOTA_EXCEEDED`, and a
positive `Retry-After` bounded by the next 00:00 UTC reset; another user remains
unaffected. Prove that successful, invalid/failed, and idempotently replayed
POSTs consume quota. Verify the three action buckets are independent and, in
the two-instance setup, usage is shared through PostgreSQL. Keep the process-
local per-client rate limit high enough not to mask quota results.

Send a valid `X-Request-ID` of up to 64 characters from `[A-Za-z0-9._-]` and
verify it is echoed on success and error and appears in server logs. Send an
invalid ID (space, control character, or over 64 characters) and verify a new
server ID is returned. Do not put source text or credentials in the ID.
`/actuator/metrics` must require authentication; after corresponding activity,
inspect `source.operation.attempts`, `source.operation.duration`,
`source.recovery.transitions`, `source.worker.queue.depth`,
`source.worker.active`, and knowledge operation/fallback meters where relevant.
Ensure tag values are low-cardinality outcomes/operations, never workspace IDs,
user emails, source content, or raw questions.

## 17. Web UI end-to-end checks

Run these in the built application at `http://localhost:8080/`, not only the Vite
development server. Test current Chrome and Edge at minimum.

### UI-01 Authentication

- Toggle between sign-in and registration.
- Register with valid credentials and verify password constraints are reflected.
- Confirm invalid credentials show a readable error.
- Confirm successful sign-in loads workspaces.
- Sign out and verify protected UI state is removed.
- Reload after sign-in; credentials are intentionally memory-only, so the user
  must sign in again.
- While signed in, invalidate credentials for a protected request and confirm an
  authenticated HTTP 401 clears the in-memory session and returns to sign-in.
  Reopen the tab to confirm credentials were not stored in local/session storage.

### UI-02 Workspace shell

- Create two workspaces, switch between them, refresh, and delete one with
  confirmation.
- Verify Library, Ask workspace, Creative studio, and Activity navigation.
- Verify the selected workspace is used by every view.
- Bookmark `#/workspaces/<id>/ask` and other view hashes, reload, and use
  browser Back/Forward; workspace and view should follow the URL. An invalid
  or deleted workspace ID must fall back safely without exposing another user.
- Verify Swagger/OpenAPI link opens.

### UI-03 Library

- Open **Add sources** and verify file, web-page, and YouTube tabs. In the file
  tab, exercise both “Process one file now” and “Background job · multiple
  media types”; the latter accepts at most one file per type and rejects empty,
  unsupported, or total `>=25 MB` submissions before network dispatch.
- Upload one direct source and one background multimodal submission. Confirm
  the Library success notice and Activity link/ID match the API response.
- Import the public web page and YouTube URL from Library; confirm the sources
  appear with the correct remote types and no stored file metadata.
- Search by partial filename and filter each source type.
- Open details and verify metadata plus recovery state, operation, attempts, next
  check, and last error when present.
- Reprocess and follow the created job in Activity.
- Delete a source with confirmation and verify it disappears.
- Confirm failed sources contribute to “Needs attention.”
- Verify source status counts (total/processed/processing/failed), empty
  search/filter state, and the source action menu at desktop and mobile widths.

### UI-04 Ask

- Submit a grounded question and verify Markdown renders safely.
- Expand source evidence and verify snippets and page/slide/sheet/time/speaker
  metadata.
- Open a safe HTTP/HTTPS source URL in a new tab.
- Verify unsafe URL schemes are never rendered as links.
- Load workspace knowledge and inspect each modality.
- Ask an unanswerable question and assess abstention.
- Ask two questions, reload and navigate away/back; recent questions and their
  saved citations should return from the server in newest-first order. Copy an
  answer and verify the clipboard contains only its text.

### UI-05 Studio

- Switch image, video, speech, and Analyze media tools. Generate an image,
  video, and speech file; preview/play and download each.
- Analyze audio, image, and video uploads. Confirm audio timed transcript
  presentation and video summary versus spoken transcript are distinguishable.
- Import the public YouTube fixture in Analyze media (also available in Library).
- Verify visual summaries and timed transcript formatting.
- Verify Studio explains when a workspace is required.
- Ensure generated media is **not** silently added to Library; upload it
  explicitly if testing subsequent ingestion.

### UI-06 Activity

- Open a background job from recent submissions.
- Reload and confirm recent jobs come from the server, not this browser session.
- Sign in from a second browser with the same user and confirm the same recent
  workspace jobs appear; switching workspaces must update the list.
- Observe polling until terminal state.
- Verify step status, timestamps, error code, and error message.
- Paste a known job ID and load it.
- Attempt another workspace's job and verify the non-disclosing error.

### UI-07 Responsive and accessible operation

At approximately 1440 px, 1024 px, and 390 px widths:

- no critical content or action is clipped;
- source tables remain usable through intended scrolling;
- forms and confirmations remain operable;
- keyboard-only navigation reaches every interactive control in logical order;
- focus is visible;
- labels are announced for inputs and tabs expose selected state;
- status/error text is not communicated by color alone;
- generated image has meaningful alternative text and audio/video controls are
  available.

Run an automated accessibility scan if available, but retain the manual keyboard
and screen-reader smoke test.

### UI-08 Mantine design and browser regression

Compare the built UI's login, registration, Library, Add source, source details,
Ask, Studio, Activity, and mobile drawer against the design intent in
`docs/design/mantine-workspace/DESIGN-SPEC.md` and its `screenshots/`.
These screenshots are a design reference, not a pixel-exact oracle: test the
shipped UI's hierarchy, branding, readable typography/contrast, spacing,
responsive layout, error/empty/loading states, and usable controls. Capture
actual screenshots at approximately 1440 px and 390 px and attach them to the
report; record any material visual regression with browser, viewport, and
reproduction steps. Verify the built production assets, not only Vite's dev UI.

## 18. Nonfunctional acceptance

Record these measurements on a warmed application with model downloads complete:

- startup time and peak memory;
- direct document, audio, image, video, and YouTube ingestion latency;
- async queue latency and per-step duration;
- question latency with query expansion enabled;
- image/video generation duration;
- recovery detection and convergence time;
- OpenSearch document count and storage growth by fixture;
- API memory after the full multimodal run.
- number of accepted/queued/retried jobs and any 429 responses;
- generation/manifest count before and after reprocessing and reconciliation.

Minimum behavioral expectations:

- no unbounded retry loop or duplicate knowledge;
- no cross-workspace or cross-user data leakage;
- no secrets or raw sensitive content in logs;
- no orphaned stored bytes after successful deletion;
- no partial indexing when required embedding generation fails;
- all long-running operations either complete, expose failure, or become
  recoverable—never remain silently stuck indefinitely.

Performance thresholds should be defined per deployment host and provider SLA.
Do not establish release gates from a single developer-laptop run.

## 19. Cleanup

1. Export the test report and non-sensitive logs/screenshots. Use a unique
   ignored `data/exports/e2e-<run-id>/` directory or another explicitly
   approved output location; do not overwrite an earlier run's artifacts.
2. Delete test workspaces through the API and verify cleanup.
3. Stop the API.
4. Stop Whisper, Piper, and OpenSearch **only if this run started those exact
   Compose projects/containers**. Confirm project names and resolved Compose
   files before running any `down`; never stop a pre-existing user instance:

   ```bash
   docker compose -f infra/docker/whisper/compose.yml down
   docker compose -f infra/docker/piper/compose.yml down
   docker compose -f infra/docker/opensearch/compose.yml down
   ```

5. Remove only the explicitly configured disposable E2E storage root after
   resolving its absolute path and verifying it is neither the workspace root
   nor an existing data directory. Prefer moving it to a recoverable location
   until the report is accepted.
6. Drop only the dedicated E2E database after verifying its exact name and
   connection target. If ownership is uncertain, leave it and report it.
7. Revoke temporary provider credentials when used.
8. Confirm no generated media, credentials, database dumps, or source content
   were added to Git.

## 20. Test report template

```markdown
# AI Workspace E2E Report

- Commit and initial/final `git status --short` (or source snapshot ID):
- Date/time and timezone:
- Tester:
- Run ID and evidence directory:
- Host OS / CPU / RAM:
- Java / Node / Docker versions:
- Profiles executed: A / B / C
- Base URL, DB name, storage root, OpenSearch host/cluster (no secrets):
- PostgreSQL version:
- OpenSearch version:
- Embedding model and dimensions:
- Provider models:
- Approved provider spend cap / observed usage or estimate:
- Fixture manifest and SHA-256 hashes:

## Summary

- Passed:
- Failed:
- Blocked:
- Not run:
- First failing test / highest defect severity:
- Release recommendation: GO / NO-GO / CONDITIONAL

## Results

| Test ID | Profile | Result | Duration | Evidence path/IDs | Expected vs actual / defect |
| --- | --- | --- | ---: | --- | --- |
| AUTH-01 | B | PASS | | | |

## AI quality review

| Question ID | Expected fact/source | Indexed? | Retrieved? | Answer grade | Citation/source correct? | Evidence/notes |
| --- | --- | --- | --- | --- | --- | --- |

## Nonfunctional measurements

| Operation | Duration | Peak memory | Provider/model | Notes |
| --- | ---: | ---: | --- | --- |

## Residual risks

-

## Cleanup and integrity

- Test workspaces, DB, storage, containers removed or intentionally retained:
- Final `git status --short` and any files created by the run:
- Secrets/provider outputs excluded from Git and shared report:
```

## 21. Release exit criteria

A release candidate passes end-to-end verification when:

1. Profile A is fully green.
2. Every applicable profile B module case passes with real dependencies.
3. Profile C confirms retry, dead-letter, lease/fencing, queued-work durability,
   reconciliation, rate-limit, quota, ownership, metrics, and HTTPS behavior in
   an isolated environment.
4. All sentinel facts are retrievable from the correct workspace and source.
5. No critical or high-severity security, data-loss, isolation, lifecycle, or
   grounding defect remains open.
6. Medium/low deviations are documented with owner and disposition.
7. Cleanup succeeds and the report contains enough evidence to reproduce the
   run.

`CONDITIONAL` is not a substitute for passing a missing high-risk case: state
exactly which profile/test is blocked and what approval or fixture is needed.
For a full E2E regression claim, no required B/C case may remain `BLOCKED` or
`NOT RUN`.
