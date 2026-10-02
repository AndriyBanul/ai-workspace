# ADR 0009: Use 768-Dimensional E5 Embeddings

## Status

Accepted

## Context

ADR 0007 introduced the 384-dimensional `intfloat/multilingual-e5-small`
model. A real-book benchmark showed that vector-only retrieval performed well
on direct questions but still missed some relevant passages. We wanted to test
whether the higher-capacity model in the same E5 family improved retrieval while
leaving chunking, candidate counts, context expansion, and answer generation
unchanged.

## Decision

Use the pinned `intfloat/multilingual-e5-base` ONNX model for local passage and
query embeddings. Store its normalized 768-dimensional vectors in the new
`knowledge-items-v3` OpenSearch index. Keep the existing E5 prefixes, token
windowing, pooling, validation, and ingestion atomicity.

Existing content must be re-ingested into the v3 index. The v2 index remains a
useful comparison data set and is not read by the application.

## Evidence

The same 580,876-character Sherlock Holmes source produced 1,397 chunks with
both models. With vector-only retrieval, 12 seed matches, and one following
chunk per seed, all 20 answer requests completed successfully. Manual grading
changed from 14 correct, 4 incomplete, and 2 failed with the 384-dimensional
model to 16 correct, 3 incomplete, and 1 failed with the 768-dimensional model.

The larger model recovered useful evidence for Julia Stoner and broadened the
cross-story disguise answer. It still failed to retrieve `221B, Baker Street`
for the address question, and exhaustive cross-story questions remained
incomplete. This is one benchmark on one English book, so it supports the model
choice without proving a general quality gain.

## Consequences

- The stored vector payload is approximately twice as large per chunk.
- The model and tokenizer cache requires about 1.13 GB.
- Local startup and embedding inference require materially more memory and CPU;
  the observed process used roughly 2.8 GB RSS during full-book ingestion on the
  development machine.
- OpenSearch mappings cannot change vector dimensions in place, so future model
  dimension changes require another index migration and complete re-ingestion.
- Retrieval evaluation should continue to separate direct lookup questions from
  exhaustive aggregation questions. Increasing vector dimensions alone does not
  solve broad coverage.
