# Testing

Every important business service should have unit tests.

Critical workflows should have integration tests.

Avoid testing framework internals.

Test coverage should scale with risk:

- Use focused unit tests for narrow business logic.
- Use integration tests for database, API, and cross-module workflows.
- Add broader tests when touching shared behavior or critical user-facing flows.

Frontend tests should prefer complete user workflows rendered with Testing
Library over assertions against component internals. Cover the API request and
visible outcome together for critical flows such as workspace lifecycle, source
ingestion/recovery/reprocessing/deletion, grounded questions, and media analysis.
Keep narrow helper tests for validation and URL handling.
