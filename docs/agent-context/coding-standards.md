# Coding Standards

## Development Principles

Prefer:

- SOLID.
- Clean Architecture.
- Domain Driven Design where appropriate.
- Hexagonal Architecture principles.
- Constructor Injection.
- Immutable DTOs.
- Stateless services.

Avoid:

- God classes.
- Business logic inside controllers.
- Static utility abuse.
- Circular dependencies.
- Tight coupling between modules.

## Coding Style

Prefer:

- Small classes.
- Small methods.
- Descriptive names.
- Readable code.
- Self-documenting code.

Avoid unnecessary comments.

## Error Handling

- Use global exception handling.
- Never swallow exceptions.
- Return meaningful error messages.
- Use structured API responses.
