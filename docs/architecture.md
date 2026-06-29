# Architecture

Current architecture:

Modular Monolith.

## Rules

- One deployable application.
- One database.
- One Git repository.
- Separate modules for each business domain.
- Modules must communicate through interfaces.
- Internal APIs should remain clean and well-defined.

Future migration to microservices should require minimal code changes.

Each future microservice should correspond to one existing module.
