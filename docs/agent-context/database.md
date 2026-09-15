# Database

- Use PostgreSQL.
- Use Flyway for migrations.
- Avoid database-specific features unless necessary.
- Never generate schema manually.
- Keep migrations reviewable and deterministic.
- Prefer explicit constraints and indexes for important domain rules and query paths.
- Store workspace ingestion-source metadata in PostgreSQL via `workspace_files`.
  Uploaded sources reference raw bytes through `FileStorage`; URL-backed web and
  YouTube sources store a URL and have null storage/checksum fields. Never store
  raw bytes in database `bytea` columns.
