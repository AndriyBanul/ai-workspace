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
