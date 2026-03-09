## Mission

- Build `lucene-clj` as an opinionated embedded retrieval layer for Clojure, not as a general wrapper over all of Lucene.
- Focus on retrieval quality and API ergonomics for application and agent workflows.
- Treat BM25-style lexical retrieval as the primary baseline.
- Support classic TF-IDF-style scoring as an explicit optional mode, mainly for comparison, compatibility, and experimentation.
- Add vector and hybrid retrieval only in ways that preserve a compact, map-first API.

## Scope Guardrails

- Prefer features that improve retrieval quality, ranking control, explainability, or developer ergonomics.
- Prefer additive abstractions over exposing raw Lucene internals.
- Every new feature should be justified by a real retrieval workflow and backed by small reproducible tests.
- Keep the library embedded and in-process by default.
- Optimize for clarity of intent in the public API, even when the underlying Lucene machinery is more complex.

## Deliberately Out Of Scope

- Full wrapper coverage of Lucene modules.
- Distributed search, cluster operations, or server-style deployment concerns.
- Solr/OpenSearch-style infrastructure features.
- Broad exposure of low-level codecs, storage internals, or specialized Lucene subsystems unless they directly serve the chosen retrieval focus.
- API growth that increases cognitive load without clear workflow value.

## Decision Filter

Before adding a feature, ask:

1. Does this improve embedded retrieval for real application or agent workflows?
2. Can it fit the map-first, low-cognitive-load API style?
3. Can we explain and test its ranking behavior?
4. Does it keep the project narrow enough to remain maintainable across Lucene upgrades?

## API Design Principles

- Prefer one canonical data model over compatibility layers when the old design obscures intent.
- One field should have one definition in one place.
- Public examples and docs should teach only the preferred API shape.
- Validate schemas at boundaries; do not add validation or coercion overhead to hot indexing loops by default.
- Any hot-path refactor should be benchmarked before and after with captured results.
