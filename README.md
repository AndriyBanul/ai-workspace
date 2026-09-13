# ai-workspace

Workspace for Java/Spring Boot services and supporting AI infrastructure.

## Structure

- `apps` - Gradle multi-module root for application code
- `apps/api` - main executable Spring Boot API subproject
- `apps/shared` - shared data model library subproject
- `apps/documents` - document business logic subproject
- `apps/images` - image business logic subproject
- `apps/videos` - video business logic subproject
- `apps/audio` - audio business logic subproject
- `apps/knowledge` - knowledge and workspace retrieval business logic subproject
- `apps/orchestrator` - async multimodal orchestration subproject
- `apps/workspaces` - workspace metadata business logic subproject
- `docs` - architecture notes, API notes, and project documentation
- `infra` - server/service configuration, SQL, and deployment assets
- `infra/docker/whisper` - optional Docker setup for local Whisper speech-to-text
- `infra/docker/piper` - optional Docker setup for local Piper text-to-speech
- `scripts` - helper scripts for local operations and deployment
- `data` - local sample data and exports

## Build

Run application build commands from `apps`:

```bash
cd apps
./gradlew test
```

## Demo UI

The API serves a simple demo console from `/` when the Spring Boot application is
running.

```bash
open http://localhost:8080/
```

## Documents

Document uploads support **TXT (plain text), PDF, DOCX, XLSX, and PPTX**.
The server detects the format from file contents; a filename or declared MIME
type does not grant support. Legacy DOC/XLS/PPT, macro-enabled Office files,
images, HTML uploads, and generic archives are not supported document formats.
Web-page imports remain available through `/api/v1/documents/web-page`. They
respect the charset declared by the origin server and HTML metadata, handle
plain-text responses without HTML parsing, and prefer `article`, `main`, or
`role=main` content. Navigation, forms, and content outside the selected region
are excluded; page-level headers and footers are also removed when body fallback
is needed. Headings, paragraphs, lists, and table rows retain readable
boundaries. The complete extracted text is stored; `storedCharacterCount` and
the compatibility `loggedCharacterCount` field therefore match
`characterCount`, and `truncated` is false.

Extraction preserves ordered text blocks instead of reducing every file to an
undifferentiated string. Blocks identify headings, paragraphs, list items, and
table rows. PDF blocks carry page numbers, presentation blocks carry slide
numbers, and spreadsheet rows carry sheet names. The knowledge text includes
page, slide, and sheet markers so the existing search pipeline retains those
locations. DOCX headings and tables are read through Apache POI; Word page
numbers are unavailable because DOCX stores document flow rather than rendered
page layout.

Direct uploads to `/api/v1/documents/text` return the usual API error response
with an additional stable `code` for document failures:

| Code | HTTP status | Action |
| --- | --- | --- |
| `EMPTY_DOCUMENT` | 400 | Select a nonempty file. |
| `UNSUPPORTED_DOCUMENT_FORMAT` | 415 | Export to a supported format. |
| `PASSWORD_PROTECTED_DOCUMENT` | 422 | Upload an unencrypted copy. |
| `CORRUPT_DOCUMENT` | 422 | Check the original file or export it again. |
| `EXTRACTION_LIMIT_EXCEEDED` | 422 | Split the document into smaller files. |
| `NO_EXTRACTABLE_TEXT` | 422 | Supply a document containing selectable text. |

Automatic OCR is disabled. A scanned or image-only PDF may need OCR before
upload; a textless result alone does not prove that a document is scanned.
Nonempty files that fail extraction remain visible with status `FAILED`, and
their content is not added to workspace knowledge. Async ingestion steps expose
the same failure code in `errorCode` alongside a readable `errorMessage`.
An explicitly attached empty document is rejected with `EMPTY_DOCUMENT` before
an ingestion job or any workspace files are created; an unselected optional
document field is still skipped.

Flyway migration `V5` adds the nullable `ingestion_job_steps.error_code` column;
existing ingestion records retain a null code.

Extraction is bounded independently of the 25 MB upload limit. By default, a
document may produce at most 1,000,000 text characters, PDF random-access
output may contain at most 10,000 structural blocks, PDF random-access buffering
spills to temporary storage after 64 MiB, and embedded attachments are ignored.
Search chunks target five complete sentences. Sentences are never cut to meet a character limit; explicit chapter/section headings and paragraph breaks are preserved.
The PDF threshold does not cap every JVM allocation made by a parser. Configure
these controls with:

```properties
ai-workspace.documents.extraction.max-extracted-characters=1000000
ai-workspace.documents.extraction.max-extracted-blocks=10000
ai-workspace.documents.extraction.max-pdf-main-memory-bytes=67108864
ai-workspace.documents.extraction.extract-embedded-documents=false
ai-workspace.documents.extraction.max-chunk-sentences=5
```

Every imported document now has a durable source identity. File uploads use the
workspace file ID; web imports receive a generated source ID and retain their
URL. Knowledge items also store the extraction completion time and the versioned
parser identifier. Direct import responses return this metadata, and asynchronous
ingestion submissions return workspace file IDs in `sourceIds`, keyed by content
type. This makes same-named files distinguishable and provides the metadata needed
for later deletion, reprocessing, and traceable citations.

## Local Whisper

Whisper is optional supporting infrastructure for the `audio` module. Start it with Docker:

```bash
docker compose -f infra/docker/whisper/compose.yml up -d
```

The service listens on `http://localhost:9000` by default.

## Local Piper

Piper is optional supporting infrastructure for text-to-speech. Start it with Docker:

```bash
docker compose -f infra/docker/piper/compose.yml up -d
```

The service listens on Wyoming protocol port `10200` by default.

## Knowledge

Create a workspace:

```bash
curl -X POST http://localhost:8080/api/v1/workspaces \
  -H 'Content-Type: application/json' \
  -d '{"name":"Investor demo workspace"}'
```

List workspaces:

```bash
curl http://localhost:8080/api/v1/workspaces
```

Get one workspace:

```bash
curl http://localhost:8080/api/v1/workspaces/{workspaceId}
```

The `knowledge` module reads workspace knowledge from OpenSearch. Configure
`OPENSEARCH_URL` if OpenSearch is not available at `http://localhost:9200`.

The API creates the vector-enabled `knowledge-items-v3` index on first access if
it does not exist. Each extracted result is stored as a separate knowledge item with
`workspaceId`, `sourceType`, `sourceName`, `sourceId`, optional `sourceUrl`,
`jobId`, `content`, `extractedAt`, `parserVersion`, chunk identity and sequence,
optional heading and source location, `embedding`, `embeddingModel`,
`embeddingDimensions`, `contentHash`, and `createdAt`.

Document and web content is indexed as bounded, structure-aware chunks rather
than one OpenSearch record per source. Each chunk retains its source ID,
sequence, current heading, and page, slide, or sheet location where available.
Each chunk receives one normalized, 768-dimensional embedding generated inside
the application by Spring AI and the pinned `intfloat/multilingual-e5-base`
ONNX model. Document chunks use the model's `passage:` prefix and questions use
its `query:` prefix. By default, search combines BM25 matches over chunk content and headings with
OpenSearch kNN results using reciprocal rank fusion. Set `KNOWLEDGE_SEARCH_MODE=VECTOR`
to use only the kNN results. In either mode, the twelve highest-ranked
chunks are selected, then the next chunk is fetched within the same
source and section. Overlapping windows are deduplicated while preserving search
rank, with each following chunk placed after its seed, producing at most 24 evidence chunks. Search falls back to BM25 when
query embedding generation is unavailable. Document ingestion fails before
indexing when the required local embedding model cannot generate every vector.

Reprocessing the same document source replaces its complete chunk set. Obsolete
chunk IDs are pruned only after OpenSearch accepts the replacement bulk request,
and a retry converges on the complete current set. Deleting a stored document
first removes its source-scoped knowledge from OpenSearch and then deletes the
stored file; if knowledge cleanup fails, the file remains available for retry.
Deleting a workspace performs the same cleanup for all of its OpenSearch
knowledge and stored files before removing the workspace metadata.

Embedding defaults are configurable with `KNOWLEDGE_EMBEDDING_MODEL`,
`KNOWLEDGE_EMBEDDING_DIMENSIONS`, `KNOWLEDGE_EMBEDDING_BATCH_SIZE`,
`KNOWLEDGE_SEARCH_CANDIDATE_LIMIT`, and
`KNOWLEDGE_SEARCH_RRF_RANK_CONSTANT`. Changing embedding dimensions requires a
new vector index and re-ingestion because OpenSearch vector dimensions are part
of the index mapping. The pinned ONNX model and tokenizer are downloaded once
and cached under `KNOWLEDGE_EMBEDDING_CACHE_DIRECTORY`; together they require
about 1.13 GB. Their URIs can point to pre-provisioned `file:` resources. Fully
offline installations must also pre-provision DJL's platform-native runtime
cache. This embedding change uses the `knowledge-items-v3` index because an
existing OpenSearch vector field cannot change from 384 to 768 dimensions.
Gemini is used for answer generation and does not create embeddings.

Text produced by document parsing/web extraction, audio transcription, image
description, and video description is attached to the selected workspace.

```bash
curl http://localhost:8080/api/v1/knowledge/workspaces/{workspaceId}
```

Ask a question against a workspace context:

```bash
curl -X POST http://localhost:8080/api/v1/knowledge/workspaces/{workspaceId}/answers \
  -H 'Content-Type: application/json' \
  -d '{"question":"What do we know about this workspace?"}'
```

Run async multimodal ingestion:

```bash
curl -X POST http://localhost:8080/api/v1/orchestrator/ingestions \
  -F "workspaceId={workspaceId}" \
  -F "document=@/path/to/document.txt" \
  -F "audio=@/path/to/audio.mp3" \
  -F "image=@/path/to/image.png" \
  -F "video=@/path/to/video.mp4"
```

The endpoint returns `202 Accepted` with an ingestion `jobId`. Check async
processing status with:

```bash
curl http://localhost:8080/api/v1/orchestrator/jobs/{jobId}
```

## Image Descriptions

The `images` module can describe uploaded images through Gemini. Configure `GEMINI_API_KEY`
before starting the API.

```bash
curl -X POST http://localhost:8080/api/v1/images/descriptions \
  -F "workspaceId={workspaceId}" \
  -F "file=@/path/to/image.png"
```

The `images` module can also generate images through FLUX.1 Dev. Configure
`HUGGING_FACE_API_TOKEN` before starting the API.

```bash
curl -X POST http://localhost:8080/api/v1/images/generations \
  -H 'Content-Type: application/json' \
  -d '{"description":"A small cabin in a snowy forest at sunrise."}' \
  --output generated-image.png
```

## Video Descriptions

The `videos` module can describe uploaded videos through Gemini. Configure
`GEMINI_API_KEY` before starting the API.

```bash
curl -X POST http://localhost:8080/api/v1/videos/descriptions \
  -F "workspaceId={workspaceId}" \
  -F "file=@/path/to/video.mp4"
```

The `videos` module can also generate videos through Google Veo. Configure
`GEMINI_API_KEY` before starting the API.

```bash
curl -X POST http://localhost:8080/api/v1/videos/generations \
  -H 'Content-Type: application/json' \
  -d '{"description":"A cinematic shot of a mountain lake at sunrise."}' \
  --output generated-video.mp4
```

For a local Docker OpenSearch instance and disposable real-server integration
tests, see [the OpenSearch setup](infra/docker/opensearch/README.md).


Long chunks remain intact in storage. For the local E5 model, all input text is
processed in token-checked windows of at most 512 tokens (including the prefix
and special tokens). Window vectors are averaged and normalized into one vector
per stored chunk; no input is silently truncated. This pooling preserves text
coverage but may dilute specific details in unusually long chunks.

Sentence boundaries use the JDK English sentence iterator with common honorific
and initial handling. Heading detection honors parser headings and conservative
plain-text/Markdown chapter markers; arbitrary prose is not a reliably inferred
section. Old indexed content must be re-ingested to obtain sentence chunks and
section IDs. Items without section metadata remain searchable but do not receive
neighbor expansion.
