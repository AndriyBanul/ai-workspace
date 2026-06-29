# Module Boundaries

- Each module should own its domain model and persistence rules.
- Cross-module communication should happen through interfaces or application services.
- Avoid direct access to another module's repositories or internal entities.
- Keep module APIs small, intentional, and stable.
- Avoid circular dependencies between modules.
- If module boundaries become unclear, stop and propose a cleaner design.

Future microservices should map naturally to existing modules, so module boundaries matter even in the modular monolith stage.
