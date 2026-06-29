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

## Future Technologies

- Kafka
- Redis
- Kubernetes
- OpenSearch
- Vector Database
- Object Storage
