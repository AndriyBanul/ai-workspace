# Initial retrieval quality evaluation

Run on 2026-09-08 against OpenSearch 3.8.0 with the pinned multilingual E5 small
model (384 dimensions). These are synthetic benchmark results, not estimates
of production accuracy.

## Decision

Keep the defaults: 2000 characters per chunk, heading weight 2, 32 candidates
per retrieval path, and RRF constant 60.

The tuning-only winner used 8 candidates with the other defaults unchanged.
It raised tuning Hit@8 from 11/12 to 12/12, but its held-out hit rates were
identical to the baseline. A 16-document corpus cannot establish the candidate
count needed for a larger collection, so the result does not justify changing
the application default.

On held-out questions, baseline hybrid retrieval achieved 9/12 at top 1,
11/12 at top 3, and 12/12 at top 8. BM25 alone achieved 7/12, 11/12, and 11/12.
The three hybrid top-1 misses concerned employee hotel allowance versus
contractor policies, sick-leave certificates, and incident update frequency.

Smaller chunks did not improve tuning performance. With otherwise default
settings, 800-character chunks achieved 9/12, 11/12, and 11/12. At 400
characters, four labelled answer phrases were split across chunks and counted
as misses. Sentence-aware boundaries or overlap deserve a separate measured
experiment before reducing chunk size.

## Coverage and limitations

The fixture has 16 synthetic policy/technical documents and 24 questions,
including near-match distractors and multilingual retrieval. Twelve questions
are used for tuning and twelve for validation; both splits use the same
documents. This is not a test of generalization to unseen domains.

Each question labels one source and an exact answer passage. Hit@K requires a
single retrieved chunk to contain that passage. The evaluator does not credit
answers assembled from adjacent partial chunks, and does not measure answer
generation or all relevant chunks. The corpus is too small to support
production claims or to establish whether reranking is necessary.

The run also found a runtime defect: non-ASCII document text was not explicitly
encoded as UTF-8 in bulk NDJSON requests. The client now sends UTF-8 bytes, with
a multilingual real-server regression check.

## Reproduce

Run ./infra/docker/opensearch/evaluate.sh from the repository root. Generated
summary.md and results.json are under apps/api/build/reports/retrieval.
The JSON records the corpus hash, pinned model URI, server version, all settings,
question ranks, expected chunk IDs and retrieved chunk IDs. See
infra/docker/opensearch/README.md for setup and cache instructions.

# Measured configurations

Synthetic corpus: 16 documents, 12 tuning questions and 12 held-out questions.

Metrics are hit rate: fraction of questions with at least one labelled chunk in the top K. This is not a production quality guarantee or recall over all relevant chunks.

Selected using tuning questions only: Config[chunkCharacters=2000, headingWeight=2, candidates=8, rrf=60]

| Split | Mode | Chunk chars | Heading weight | Candidates | RRF | Hit@1 | Hit@3 | Hit@8 |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| tune | BM25 | 2000 | 2 | 32 | 60 | 75.0% | 83.3% | 91.7% |
| tune | hybrid | 2000 | 2 | 32 | 60 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 2 | 32 | 10 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 2 | 16 | 60 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 2 | 16 | 10 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 2 | 8 | 60 | 83.3% | 91.7% | 100.0% |
| tune | hybrid | 2000 | 2 | 8 | 10 | 83.3% | 91.7% | 100.0% |
| tune | BM25 | 2000 | 1 | 32 | 60 | 75.0% | 83.3% | 91.7% |
| tune | hybrid | 2000 | 1 | 32 | 60 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 1 | 32 | 10 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 1 | 16 | 60 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 1 | 16 | 10 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 1 | 8 | 60 | 83.3% | 91.7% | 100.0% |
| tune | hybrid | 2000 | 1 | 8 | 10 | 83.3% | 91.7% | 100.0% |
| tune | BM25 | 2000 | 4 | 32 | 60 | 75.0% | 83.3% | 91.7% |
| tune | hybrid | 2000 | 4 | 32 | 60 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 4 | 32 | 10 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 4 | 16 | 60 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 4 | 16 | 10 | 83.3% | 91.7% | 91.7% |
| tune | hybrid | 2000 | 4 | 8 | 60 | 83.3% | 91.7% | 100.0% |
| tune | hybrid | 2000 | 4 | 8 | 10 | 83.3% | 91.7% | 100.0% |
| tune | BM25 | 800 | 2 | 32 | 60 | 66.7% | 75.0% | 91.7% |
| tune | hybrid | 800 | 2 | 32 | 60 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 2 | 32 | 10 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 2 | 16 | 60 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 2 | 16 | 10 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 2 | 8 | 60 | 75.0% | 91.7% | 100.0% |
| tune | hybrid | 800 | 2 | 8 | 10 | 75.0% | 91.7% | 100.0% |
| tune | BM25 | 800 | 1 | 32 | 60 | 66.7% | 75.0% | 91.7% |
| tune | hybrid | 800 | 1 | 32 | 60 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 1 | 32 | 10 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 1 | 16 | 60 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 1 | 16 | 10 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 1 | 8 | 60 | 75.0% | 91.7% | 100.0% |
| tune | hybrid | 800 | 1 | 8 | 10 | 75.0% | 91.7% | 100.0% |
| tune | BM25 | 800 | 4 | 32 | 60 | 66.7% | 75.0% | 91.7% |
| tune | hybrid | 800 | 4 | 32 | 60 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 4 | 32 | 10 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 4 | 16 | 60 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 4 | 16 | 10 | 75.0% | 91.7% | 91.7% |
| tune | hybrid | 800 | 4 | 8 | 60 | 75.0% | 91.7% | 100.0% |
| tune | hybrid | 800 | 4 | 8 | 10 | 75.0% | 91.7% | 100.0% |
| tune | BM25 | 400 | 2 | 32 | 60 | 41.7% | 50.0% | 58.3% |
| tune | hybrid | 400 | 2 | 32 | 60 | 50.0% | 58.3% | 58.3% |
| tune | hybrid | 400 | 2 | 32 | 10 | 50.0% | 58.3% | 58.3% |
| tune | hybrid | 400 | 2 | 16 | 60 | 50.0% | 58.3% | 58.3% |
| tune | hybrid | 400 | 2 | 16 | 10 | 50.0% | 58.3% | 66.7% |
| tune | hybrid | 400 | 2 | 8 | 60 | 50.0% | 66.7% | 66.7% |
| tune | hybrid | 400 | 2 | 8 | 10 | 50.0% | 66.7% | 66.7% |
| tune | BM25 | 400 | 1 | 32 | 60 | 41.7% | 50.0% | 58.3% |
| tune | hybrid | 400 | 1 | 32 | 60 | 58.3% | 58.3% | 58.3% |
| tune | hybrid | 400 | 1 | 32 | 10 | 58.3% | 58.3% | 58.3% |
| tune | hybrid | 400 | 1 | 16 | 60 | 58.3% | 58.3% | 58.3% |
| tune | hybrid | 400 | 1 | 16 | 10 | 58.3% | 58.3% | 66.7% |
| tune | hybrid | 400 | 1 | 8 | 60 | 58.3% | 66.7% | 66.7% |
| tune | hybrid | 400 | 1 | 8 | 10 | 58.3% | 66.7% | 66.7% |
| tune | BM25 | 400 | 4 | 32 | 60 | 16.7% | 50.0% | 58.3% |
| tune | hybrid | 400 | 4 | 32 | 60 | 41.7% | 58.3% | 58.3% |
| tune | hybrid | 400 | 4 | 32 | 10 | 41.7% | 58.3% | 58.3% |
| tune | hybrid | 400 | 4 | 16 | 60 | 41.7% | 58.3% | 58.3% |
| tune | hybrid | 400 | 4 | 16 | 10 | 41.7% | 58.3% | 66.7% |
| tune | hybrid | 400 | 4 | 8 | 60 | 41.7% | 66.7% | 66.7% |
| tune | hybrid | 400 | 4 | 8 | 10 | 41.7% | 66.7% | 66.7% |
| holdout | BM25 | 2000 | 2 | 32 | 60 | 58.3% | 91.7% | 91.7% |
| holdout | hybrid | 2000 | 2 | 32 | 60 | 75.0% | 91.7% | 100.0% |
| holdout | BM25 | 2000 | 2 | 8 | 60 | 58.3% | 91.7% | 91.7% |
| holdout | hybrid | 2000 | 2 | 8 | 60 | 75.0% | 91.7% | 100.0% |

See results.json for question-level ranks, expected chunk IDs and retrieved IDs.
