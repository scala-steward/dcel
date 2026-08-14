# ADR-0020: Patch symmetry orbits as the answer for non-periodic patches

- **Status:** Accepted
- **Date:** 2026-08-14

## Context and problem statement

ADR-0019 replaced heuristic uniformity with the exact Delaney–Dress
computation and accepted, as a deliberate negative consequence, that
`delaneyVertexClasses` returns `Left(PeriodicityError(…))` when no
translation lattice is detectable: "uniformity of a non-periodic or
boundary-dominated patch is ill-defined, and this design makes that honest
instead of silently wrong."

Consumers then have to decide what to draw. Both current call sites made the
same choice, and it undoes that honesty:

```scala
tiling.delaneyVertexClasses().getOrElse(tiling.uniformityTree.flattenLeaves…)
```

— `TilingSVG.createVertexElements` here, and the editor's uniformity overlay
(`SymmetryOperations.toggleShowUniformity`). The `getOrElse` discards an
error that names the exact failure and substitutes the heuristic grouping
that ADR-0019 documented as wrong by construction: rim-only comparison is
not patch isomorphism, `bracelet` erases chirality, the class count is a
function of patch size, hole rims are misread as interior. The user is shown
a colouring, told how many classes were found, and given no signal that the
number came from a superseded computation answering a different question.

Two further points sharpen the problem:

- **The failure is usually not "aperiodic art".** Of the six ways the
  pipeline declines — empty patch, no lattice, false period, patch not
  covering a fundamental domain, a vertex with no incident inner face,
  non-euclidean or non-regular-polygon classification — the dominant ones in
  an interactive editor are "no lattice" and "no fundamental domain", both
  of which usually mean *the patch is too small yet*. The fallback fires
  most often on a perfectly periodic tiling under construction.
- **A single polygon is the extreme case.** One tile has no detectable
  lattice at all, yet its vertex classes are obvious to anyone looking at
  it.

So the question is not how to approximate tiling uniformity when it is
undefined. It is what *well-posed* question about a finite patch to answer
instead.

## Decision

**Add `TilingPatchSymmetry`: the isometry group of the finite patch, found
geometrically, and the vertex orbits under it.** This is the surface
consumers reach for when `TilingDelaney` declines, replacing the deprecated
`uniformityTree` fallback.

Two vertices share an orbit exactly when some rigid motion of the plane that
maps the patch onto itself carries one to the other. That is well posed for
every patch, periodic or not, needs no lattice, and classifies every vertex
including boundary ones.

The search is cheap because the answer is constrained: every isometry of a
bounded figure fixes its centroid, so the group is finite and either cyclic
or dihedral (Leonardo's theorem). A symmetry must permute the vertices at
maximal distance from the centroid, so fixing one such reference vertex
leaves at most one rotation and one reflection per candidate image. Each
candidate is confirmed by applying it to the whole patch: it must map the
vertex set onto itself and carry every edge to an edge. An isometry
preserving both determines the planar embedding, so faces need no separate
check.

The public surface is `patchSymmetryGroup` — the group as `Cn` or `Dn` — and
`patchVertexClasses` — the orbits as contiguous indices ordered by each
orbit's lowest `VertexId`. Neither returns `Either`: for a non-empty patch
the identity is always a symmetry, so an answer always exists.

Matching is tolerance-based rather than exact. Coordinates are
`BigDecimal`-typed but produced through `Double` trigonometry and carry
~1e-12 of error (ADR-0009) — the same reason `TilingDelaney` keys its
quotient on rounded coordinates. The candidate matrix is renormalised so
that error does not grow with the patch radius.

## Consequences

### Positive

- **Every patch gets an answer, and it is the answer to a stated question.**
  No patch is left with a colouring produced by a computation whose
  semantics nobody can state.
- **The common fallback cases become genuinely useful.** A single hexagon —
  where the Delaney pipeline correctly has nothing to say — resolves to one
  orbit rather than to a heuristic grouping.
- **`TilingSymmetry` gains the piece it was missing.** It already targets
  the finite patch and reports rotational order and reflection axes, but not
  the induced vertex permutations, so it could not produce orbits.
  `patchSymmetryGroup().rotationOrder` agrees with its
  `rotationalSymmetryOrder` on the fixtures, cross-checking two independent
  routes to the same quantity — one combinatorial over the boundary, one
  geometric over the whole patch.
- **The last consumer of the deprecated heuristic can go.** Once
  `TilingSVG` and the editor switch, `uniformityTree` has no non-deprecated
  caller left, clearing the way for the removal ADR-0019 deferred.

### Negative / risks

- **The orbits are not uniformity, and must not be presented as such.** A
  finite net has only the symmetry of its own outline: a 6×6 triangle net is
  1-uniform as a tiling but splits into 16 patch orbits. Whenever
  `delaneyVertexClasses` has an answer it remains the one to use; this is
  strictly the fallback. Callers should say which of the two they are
  showing.
- **An asymmetric patch produces one orbit per vertex.** That is the honest
  answer — the patch genuinely has no symmetry — but it is a rainbow rather
  than a useful colouring. `patchSymmetryGroup().isTrivial` lets a caller
  detect the case and say so instead of drawing it.
- **Cost is O(outermost × vertices)** with a full pass per candidate. The
  outermost set is small in practice, and measured runtimes are tens of
  milliseconds on patches larger than interactive editing produces, but the
  worst case is quadratic on a patch whose vertices are near-cocircular.
- **Tolerance, not exactness.** Unlike the combinatorial Delaney pipeline
  this compares coordinates, so it inherits ADR-0009's precision envelope.
  The failure mode is a missed symmetry on a patch far larger than the
  coordinate error budget supports, not a wrong one.

## Alternatives considered

- **Keep the `uniformityTree` fallback.** Rejected: ADR-0019 already
  established the result is wrong by construction and deprecated the API;
  continuing to draw it silently is the specific problem this ADR closes.
- **Report the error and draw nothing.** Considered, and it is what a caller
  should do when the patch group is trivial. Rejected as the general
  behaviour because it discards a real answer in the cases that matter most
  — a single polygon, a small symmetric cluster — where the patch group is
  large and informative.
- **Repair the colour refinement instead** — compare patch interiors, use
  chirality-aware keys, mark boundary-truncated vertices as unclassified.
  Rejected on the same grounds as in ADR-0019, with one addition: the
  repaired quantity would still be a local-similarity measure that is easy
  to mistake for uniformity, whereas "orbits under the patch's own symmetry
  group" states its own scope.
- **Derive the orbits from the combinatorial automorphism group** of the
  planar map rather than geometrically. Rejected: for a rigid unit-edge
  patch the two coincide, and the geometric route reuses existing exact
  vector arithmetic instead of adding a graph-isomorphism engine.

## Related

- ADR-0004 (`Either`-based errors — how the Delaney pipeline declines).
- ADR-0009 (precision — why matching is tolerance-based).
- ADR-0019 (exact uniformity — the decision that created the undefined case
  this one fills, and deprecated the fallback it replaces).
