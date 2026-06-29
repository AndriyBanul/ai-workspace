# Deployment

Default server deployment should use native/system services.

Docker may be used only when explicitly requested or when it is clearly the better local development option.

Current explicit Docker exception:

- Whisper speech-to-text service for the `audio` module, defined in `infra/docker/whisper/compose.yml`.

Deployment-related changes should consider:

- Service startup and restart behavior.
- Configuration management.
- Secrets management.
- Database migrations.
- Logs and diagnostics.
- Rollback or recovery strategy.

Do not assume Kubernetes, Docker, Kafka, or other infrastructure before the product actually needs it.
