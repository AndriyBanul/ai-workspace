# Database

- Use PostgreSQL.
- Use Flyway for migrations.
- Avoid database-specific features unless necessary.
- Never generate schema manually.
- Keep migrations reviewable and deterministic.
- Prefer explicit constraints and indexes for important domain rules and query paths.
- Store workspace file metadata in PostgreSQL via `workspace_files`; store raw bytes through `FileStorage`, not in database `bytea` columns.
