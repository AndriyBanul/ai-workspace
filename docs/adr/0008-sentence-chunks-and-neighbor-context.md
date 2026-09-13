# ADR 0008: Sentence chunks and neighboring context

Status: Accepted

## Decision

Group up to five complete sentences per document chunk, preserving paragraph
breaks and detected chapter/section boundaries. Preserve source locations and
store a section identifier with each chunk. Sentence length has no character
cap; document extraction resource limits still apply.

Select twelve retrieval matches. Fetch one following
chunk per match from the same workspace, source and section. Deduplicate and
preserve the retrieval rank of each seed, placing its following chunk directly
after it before answer generation (at most 24 chunks). Old items without section
metadata remain searchable without expansion.

The local E5 model has a finite input capacity. Tokenize oversized inputs into
inference windows covering the full text; average their normalized embeddings
and normalize again to retain one vector per stored chunk. Do not truncate or
split the stored sentence. Query inputs use the same capacity handling.

## Consequences

Smaller matched passages gain nearby narrative context. Long-chunk pooled vectors
may dilute individual details. English sentence detection and plain-text heading
recognition are heuristics; source formatting matters. Exhaustive questions across
many stories still need a broader retrieval strategy. Existing documents require
reingestion. A count of twelve matches does not bound total evidence tokens.

Real-server tests cover neighbor isolation and deduplication. The earlier five-match, both-neighbors synthetic
held-out evaluation obtained Hit@1/3/5 of 50%/91.7%/100%; the previous configuration
had 75% Hit@1, so this change does not establish a universal quality improvement.
