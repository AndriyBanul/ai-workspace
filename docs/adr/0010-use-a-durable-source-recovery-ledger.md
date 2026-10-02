# ADR 0010: Use a Durable Source Recovery Ledger

## Status

Accepted

## Context

Workspace source metadata is stored in PostgreSQL, raw uploaded bytes are held
behind `FileStorage`, and extracted knowledge is indexed in OpenSearch. A process
can fail or restart between changes to those systems. Source status alone cannot
represent the active operation, retry schedule, lease, failure history, and
periodic integrity checks without coupling workspace metadata to orchestration.

Processing and deletion must be safe to repeat. Operators also need to distinguish
work awaiting retry from a permanent failure instead of inferring it from logs.

## Decision

Add one PostgreSQL `source_recovery_tasks` row per workspace source, owned by the
orchestrator module. The row records:

- the current `PROCESS` or `DELETE` operation;
- `SCHEDULED`, `RUNNING`, `COMPLETED`, or `DEAD_LETTER` status;
- attempt count, next attempt time, and running lease expiry;
- a bounded last error code and message.

Transient I/O and upstream failures use bounded exponential backoff. Permanent
input/processing errors and exhausted retries move directly to dead letter.
Expired leases are eligible for recovery after a process interruption.

Completed processing tasks become due for periodic reconciliation. The
reconciler verifies stored bytes for uploaded sources and indexed knowledge for
all processed sources. It schedules replacement reprocessing for missing
knowledge, dead-letters missing stored content as an integrity incident, and
removes orphaned knowledge when source metadata no longer exists.

Processing replaces all OpenSearch items for the stable source ID. Deletion
removes knowledge and storage only if present. These semantics make retries
idempotent. The current task state is exposed through an owner-scoped API and the
Library source detail.

## Consequences

- Recovery survives application restarts and is diagnosable without reading
  process-local logs.
- Source metadata stays focused on the user-facing lifecycle while orchestration
  owns operational retry state.
- PostgreSQL is the recovery system of record; OpenSearch and file storage are
  checked rather than treated as transaction participants.
- This is eventual consistency, not a distributed transaction. Integrity gaps
  can exist until the next retry or reconciliation interval.
- Retry policy is deliberately conservative: unknown failures dead-letter until
  explicitly classified as transient.
- Workspace-wide deletion remains a synchronous aggregate operation; the durable
  ledger currently operates at source granularity.
