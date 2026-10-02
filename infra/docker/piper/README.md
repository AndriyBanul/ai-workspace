# Piper TTS

Docker setup for a local Piper text-to-speech service exposed through the Wyoming protocol.

Start from the repository root:

```bash
docker compose -f infra/docker/piper/compose.yml up -d
```

Defaults:

- Wyoming protocol port: `10200`
- Voice: `en_US-lessac-medium`

Override defaults when starting the service:

```bash
PIPER_VOICE=en_US-lessac-medium PIPER_PORT=10200 docker compose -f infra/docker/piper/compose.yml up -d
```

Stop the service:

```bash
docker compose -f infra/docker/piper/compose.yml down
```
