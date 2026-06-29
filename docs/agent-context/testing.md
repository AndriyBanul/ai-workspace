# Testing

Every important business service should have unit tests.

Critical workflows should have integration tests.

Avoid testing framework internals.

Test coverage should scale with risk:

- Use focused unit tests for narrow business logic.
- Use integration tests for database, API, and cross-module workflows.
- Add broader tests when touching shared behavior or critical user-facing flows.
