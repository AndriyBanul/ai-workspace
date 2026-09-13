# Local OpenSearch

Start the persistent application instance from the repository root:

```bash
docker compose -f infra/docker/opensearch/compose.yml up -d --wait
```

OpenSearch 3.8.0 is available at http://localhost:9200, matching the application's
default OPENSEARCH_URL. Data lives in the dedicated Compose volume.
Security is disabled for local development and the host port binds only to
127.0.0.1. This configuration is not for production.

Stop it while preserving data:

```bash
docker compose -f infra/docker/opensearch/compose.yml down
```

## Real integration tests

From the repository root:

```bash
./infra/docker/opensearch/test.sh
```

The script starts a separate cluster on localhost:19200, waits for readiness,
runs the knowledge module's openSearchIntegrationTest Gradle task, and removes
the test container and its data on exit, including after test failures. It does
not stop or reset the application instance on port 9200. Run one test script at
a time because the test Compose project and port are fixed.

The tests check the cluster name before resetting the test index. They verify
mapping settings, bulk metadata persistence, BM25 and kNN contributions,
nearest-vector ordering, workspace isolation, source replacement, and source
and workspace deletion. Fixed vectors make these checks independent of model
downloads, credentials, or embedding quality.

Normal ./gradlew test excludes the opensearch tag and requires no Docker.
A CI pipeline with Docker should invoke test.sh explicitly; these checks are
not silently skipped when Docker is unavailable.

## Retrieval quality evaluation

Run from the repository root:

```bash
./infra/docker/opensearch/evaluate.sh
```

This uses the same disposable test cluster; do not run it concurrently with
test.sh. It executes the real DocumentChunker, Spring AI E5 adapter and production
OpenSearch client. The pinned model URLs come from application.properties.
The first run downloads the model and native libraries; subsequent runs reuse
the cache. Set KNOWLEDGE_EMBEDDING_CACHE_DIRECTORY to reuse an existing cache.

Reports are written to apps/api/build/reports/retrieval/summary.md and
results.json. The JSON includes every question's first relevant rank (0 means
no hit within the top 5), expected chunk IDs, and retrieved chunk IDs. An empty
expected-chunk list means chunking split the labelled passage; this counts as a
miss. Labels are checked against the original documents before evaluation.

The versioned corpus in apps/api/src/test/resources/retrieval/corpus.json is
synthetic. It contains 16 documents, 12 tuning questions and 12 held-out
questions, including a cross-language question and near-match distractors.
This is a starting benchmark, not representative production evidence.

The grid compares 3/5/8 sentence chunks, heading weights 1/2/4,
candidate counts 8/16/32 and RRF constants 10/60. BM25-only is also measured.
Choose the hybrid configuration by tuning Hit@1, then Hit@3, then Hit@5;
ties favor the existing configuration. Only the baseline and selected
configuration are evaluated on held-out questions. Both splits share the
documents; held-out questions are not a test of unseen domains.

Hit@K is the fraction of questions with at least one complete labelled answer
passage from the correct source in the first K results. It does not measure
answer generation, all relevant chunks, abstention on unanswerable questions,
latency, or large-corpus recall. A permissive 75% held-out hybrid expanded-context coverage gate
detects major regressions; it is not a production acceptance target. Expand the
corpus with reviewed real questions before changing production defaults.

Current reports distinguish Hit@1/3/5 for matched chunks from context-hit coverage after same-section neighbor expansion. Historical character-chunk results remain in docs/evaluations/retrieval-baseline.md.

Retrieval now selects up to 12 matches, then adds only the following chunk in the same section (at most 24 evidence chunks after deduplication). Hit@1/3/5 still measures the first 1/3/5 seeds; context hit measures the expanded set.
