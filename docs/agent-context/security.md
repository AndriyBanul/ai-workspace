# Security

- Never commit secrets, tokens, private keys, or real credentials.
- Use `.env.example` for documented configuration only.
- Treat uploaded documents and enterprise data as sensitive by default.
- Validate file uploads and external inputs.
- Prefer least-privilege access for integrations and tokens.
- Do not log sensitive data.
- Do not expose internal services to the public internet without explicit approval.
- Keep authentication, authorization, and auditability in mind for business workflows.

Security-sensitive changes should be explained clearly before implementation when trade-offs exist.
