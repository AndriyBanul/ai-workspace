# Technology Stack

## Backend

- Java 21+
- Spring Boot
- Gradle multi-module build rooted at `apps`
- Spring Data JPA
- PostgreSQL
- Flyway
- REST APIs

Build commands should be run from `apps`, for example:

```bash
cd apps
./gradlew test
```

## Deployment

Server deployment defaults to native/system services unless Docker is explicitly requested.

Docker may be used for local development, optional packaging, or isolated supporting services when appropriate.

The local Whisper speech-to-text service is Docker-based because Андрій explicitly requested Docker for this component. Its compose file lives at `infra/docker/whisper/compose.yml`.

The local Piper text-to-speech service is also Docker-based by explicit request. Its compose file lives at `infra/docker/piper/compose.yml`.

## Future Technologies

- Kafka
- Redis
- Kubernetes
- OpenSearch
- Vector Database
- Object Storage
