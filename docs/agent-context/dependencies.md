# Dependencies

- Do not add new dependencies without a clear reason.
- Prefer standard Spring Boot and JDK capabilities when sufficient.
- Explain why a new dependency is needed and what trade-off it introduces.
- Avoid dependencies that are unmaintained, overly broad, or difficult to replace.
- Keep dependency choices compatible with long-term production maintenance.

New dependencies should reduce meaningful complexity, not hide unclear design.

- `apps/documents` uses Apache Tika standard parsers for document text extraction. This keeps TXT, PDF, DOCX, XLSX, and PPTX support in-process for the MVP without changing the document service contract.
- `apps/documents` declares Apache POI OOXML directly for DOCX structural extraction. Tika remains responsible for content detection and general parsing, while POI preserves DOCX heading styles, lists, paragraphs, and tables that Tika's generic XHTML events flatten.
- `apps/knowledge` uses Spring AI's embedding API, while `apps/api` owns the Spring AI Transformers starter. This runs the pinned multilingual E5 ONNX model in-process and avoids an external embedding API, at the cost of a larger application artifact and local model/runtime cache requirements. See ADR 0007.
