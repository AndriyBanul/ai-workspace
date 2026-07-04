# Dependencies

- Do not add new dependencies without a clear reason.
- Prefer standard Spring Boot and JDK capabilities when sufficient.
- Explain why a new dependency is needed and what trade-off it introduces.
- Avoid dependencies that are unmaintained, overly broad, or difficult to replace.
- Keep dependency choices compatible with long-term production maintenance.

New dependencies should reduce meaningful complexity, not hide unclear design.

- `apps/documents` uses Apache Tika standard parsers for document text extraction. This keeps TXT, PDF, DOCX, XLSX, and PPTX support in-process for the MVP without changing the document service contract.
