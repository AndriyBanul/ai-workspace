# ADR 0007: Use Spring AI for Local Text Embeddings

## Status

Accepted. The model, dimensions, and index version are superseded by ADR 0009;
the local Spring AI architecture and ingestion behavior remain current.

## Context

Knowledge search requires a vector for every indexed chunk and for each semantic
query. Generating those vectors through Gemini couples document ingestion to an
external API, adds rate limits and request cost, and prevents fully local
embedding generation. The application should generate embeddings in-process
while keeping the knowledge service behind its existing provider-neutral
`TextEmbeddingProvider` interface.

## Decision

Use Spring AI 2.0.1 `TransformersEmbeddingModel` to run an ONNX embedding model
inside the Spring Boot application. Use the pinned
`intfloat/multilingual-e5-small` model revision, which produces 384-dimensional
vectors and supports multilingual retrieval.

Prefix indexed chunk text with `passage: ` and search text with `query: ` as
required by the E5 retrieval model. Apply attention-mask-aware mean pooling in
Spring AI and normalize every vector before sending it to OpenSearch.

Keep Spring AI's provider-neutral embedding API dependency in `apps/knowledge`.
Keep its transformer starter and runtime auto-configuration in the executable
`apps/api` module. Continue exposing local embeddings to knowledge business
logic only through `TextEmbeddingProvider`.

Treat embeddings as required during ingestion. Generate every chunk vector
before indexing any replacement chunk set, and fail the ingestion operation if
the model is unavailable or returns an invalid vector. Query embedding failures
may fall back to BM25 so existing indexed content remains searchable.

Use the new `knowledge-items-v2` OpenSearch index with a 384-dimensional vector
mapping. OpenSearch cannot change the dimension of an existing vector field.

## Alternatives Considered

### Gemini Embedding API

This reused the existing Gemini integration, but document ingestion depended on
network availability, provider quotas, and per-request pricing.

### Direct ONNX Runtime Integration

This could reduce framework surface area, but the application would need to own
tokenization, attention masks, mean pooling, batching, and model lifecycle code.
Spring AI already supplies these pieces behind its stable `EmbeddingModel` API.

### Separate Local Embedding Service

A Python, Ollama, or model-server process would isolate model resources and
scale independently, but it adds another deployed service and network boundary.
That complexity is not justified for the current application stage.

## Consequences

Positive consequences:

- Embedding generation has no per-request external AI API dependency or cost.
- Multilingual semantic retrieval remains available through 384-dimensional
  vectors, which use less OpenSearch storage than the previous 768 dimensions.
- The provider-neutral application interface remains replaceable.
- A pinned model revision makes vector generation reproducible.

Trade-offs:

- The executable application artifact grows because it includes ONNX and DJL
  runtime dependencies.
- The model and tokenizer require about 487 MB of cache space, and DJL requires
  platform-native runtime files.
- Initial startup needs network access unless all model and native runtime files
  are pre-provisioned.
- Embedding inference consumes local CPU and memory and will eventually need
  production latency and throughput measurements.
- Changing the embedding model or its dimensions requires a new OpenSearch
  index and complete re-ingestion.
