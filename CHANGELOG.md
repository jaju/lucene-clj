# CHANGELOG

## Unreleased

### Changed
- The next release line moves to `0.3.0-SNAPSHOT`.
- Performance work is now tracked with captured benchmark results under `benchmarks/`.

### Planned Breaking Changes
- Indexing is moving to a canonical `:fields` schema where each field is defined in one place.
- The older bucket-based indexing options are being removed rather than carried forward as compatibility baggage.

### Why
- The old indexing API split one field's meaning across multiple option collections.
- That made the code harder to validate, harder to extend, and harder to explain.
- The new direction favors one explicit schema per field, with validation at the edges and a fast compiled indexing core.
