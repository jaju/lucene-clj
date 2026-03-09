# CHANGELOG

## Unreleased

### Changed
- The next release line moves to `0.3.0-SNAPSHOT`.
- Performance work is now tracked with captured benchmark results under `benchmarks/`.
- Indexing now uses a canonical `:fields` schema where each field is defined in one place.
- Canonical field specs now support `:long` and `:boolean` with exact-match search.

### Removed
- The older bucket-based indexing options:
  - `:indexed-fields`
  - `:stored-fields`
  - `:keyword-fields`
  - `:suggest-fields`
  - `:context-fn`

### Why
- The old indexing API split one field's meaning across multiple option collections.
- That made the code harder to validate, harder to extend, and harder to explain.
- The new direction favors one explicit schema per field, with validation at the edges and a fast compiled indexing core.
