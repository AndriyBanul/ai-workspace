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

The API creates the `knowledge-items` index on first access if it does not
exist. Each extracted result is stored as a separate knowledge item with
`workspaceId`, `sourceType`, `sourceName`, `jobId`, `content`, and `createdAt`.

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
