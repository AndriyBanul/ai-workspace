# Whisper ASR

Docker setup for a local Whisper-compatible speech-to-text service.

Start from the repository root:

```bash
docker compose -f infra/docker/whisper/compose.yml up -d
```

Defaults:

- HTTP port: `9000`
- ASR engine: `faster_whisper`
- ASR model: `tiny`

Override defaults when starting the service:

```bash
WHISPER_ASR_MODEL=base WHISPER_PORT=9000 docker compose -f infra/docker/whisper/compose.yml up -d
```

Transcribe an audio file:

```bash
curl -sS -X POST "http://localhost:9000/asr?task=transcribe&output=json" \
  -F audio_file=@/path/to/audio.wav
```

Stop the service:

```bash
docker compose -f infra/docker/whisper/compose.yml down
```
