# Observability

- Add structured logs for important workflows.
- Log business-relevant events without leaking sensitive data.
- Make failures diagnosable from logs and API responses.
- Design services so metrics and tracing can be added later.
- Include correlation or request identifiers when practical.
- Prefer actionable error logs over noisy stack traces.

Production-quality MVP features should be understandable during failure, not only during happy-path execution.

Source recovery state is queryable through the workspace source recovery API
and visible in the Library source detail. `SCHEDULED`, `RUNNING`, `COMPLETED`,
and `DEAD_LETTER` make retry and reconciliation outcomes explicit; attempts,
next run/lease timestamps, and bounded errors provide operational context.
Scheduler dispatch failures log workspace ID, source ID, operation, and status.
