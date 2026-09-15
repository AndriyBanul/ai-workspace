# Security

- Never commit secrets, tokens, private keys, or real credentials.
- Use `.env.example` for documented configuration only.
- Treat uploaded documents and enterprise data as sensitive by default.
- Validate file uploads and external inputs.
- Prefer least-privilege access for integrations and tokens.
- Do not log sensitive data.
- Do not expose internal services to the public internet without explicit approval.
- Keep authentication, authorization, and auditability in mind for business workflows.

## Current HTTP Security Baseline

- HTTP Basic authentication is stateless: the API does not create server-side
  sessions and the browser keeps credentials only in memory.
- Production traffic must use TLS. `SECURITY_REQUIRE_HTTPS` can enforce secure
  channels after reverse-proxy forwarded headers are configured correctly.
- New passwords require at least 12 characters and at most 72 UTF-8 bytes;
  BCrypt strength defaults to 12. Existing BCrypt hashes remain compatible.
- Workspace ownership is checked in `WorkspaceService` before files, sources,
  ingestion jobs, or knowledge are accessed. Cross-owner lookups return 404 to
  avoid disclosing resource existence.
- `SECURITY_AUDIT` records authentication failures, rejected authenticated
  resource access, API mutations, and rate-limit denials. It logs only hashed
  actor/client fingerprints and never request bodies, credentials, query strings,
  or email addresses.
- API and registration rate limits are configurable. The current implementation
  is process-local and keyed from the direct peer address; use a trusted edge or
  distributed limiter before running multiple API instances.

Security-sensitive changes should be explained clearly before implementation when trade-offs exist.
