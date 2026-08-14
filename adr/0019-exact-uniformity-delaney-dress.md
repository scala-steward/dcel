# ADR-0019: Exact uniformity via a ported Delaney–Dress symbol subset

- **Status:** Accepted
- **Date:** 2026-08-14

## Context and problem statement

Uniformity — the number of vertex orbits under the symmetry group of the
tiling — is currently computed by `TilingUniformity` as **colour refinement
on boundary signatures of BFS balls**: vertices are grouped by comparing
their local patches at growing radius, where the comparison key is the
patch's outer rim only (`TilingEquivalency.groupByBoundaryEquivalency`, the
cyclic sequence of rim-vertex angle bracelets). An August 2026 review found
this wrong by construction, not merely buggy at the edges:

- **Rim-only equality is not patch isomorphism.** The interior is never
  compared ("the inner elements of the structures are assumed equal" —
  `TilingEquivalency.scala`); distinct neighbourhoods with coincident rim
  angle-rings merge into one class.
- **`bracelet` erases chirality.** The key is canonical under rotation *and*
  reflection, so mirror-image classes merge even when no symmetry of the
  tiling identifies them.
- **The answer is a function of patch size, not of the tiling.** Refinement
  stops when patches hit the tiling boundary ("stuck" vertices), so the
  result is at best a lower bound that grows with the patch fed in. Stuck
  vertices parked in branch values are invisible to `sizeLeaves`; the
  `maxDistance` path can emit `Leaf(Nil)` — an empty class counted as a
  class.
- **Holes are mishandled.** Only `outerFace.outerComponent` counts as
  boundary, so hole-rim vertices are classified as interior with silently
  truncated patches.
- **A concrete regression is on record.** The commented-out test at
  `TilingUniformitySpec.scala` ("problematic tiling", fixture
  `metadata/3.6.3.6_uniformity_issue.xml`) documents the trihexagonal
  tiling — uniformity 1 — reported as 2 classes. It was never re-enabled.

Structurally, `TilingUniformity` never touches `TilingLattice`: uniformity
of a tiling is defined by orbits on its *periodic* structure, and the code
computes it without using the periodicity machinery the library already has
(ADR-0015, ADR-0018).

An exact, finite, patch-independent characterisation exists. A tiling's
**minimal Delaney–Dress symbol** is a complete invariant, and its
{σ₁,σ₂}-orbits (vertex orbits) are precisely the uniformity — the
Krötenheerdt quantities, independent of the chosen cell. The sibling
`research-core` library (Apache-2.0, same organisation) carries a mature
engine for this in `DelaneySymbols`, including two entry points written as
an external bridge and so far uncalled by anything:

- `closedMapSymbol(op)` — wraps the three chamber involutions of a closed
  oriented map (the barycentric subdivision a DCEL *is*) as a `v = 1`
  symbol; and
- `classifyClosedMap(op)` — the full pipeline: symbol → euclidicity check →
  regular-polygon vertex check → `minimalSymbol` → `(n, vertex signatures,
  canonical key)`, where `n` is the uniformity, exactly.

The chamber encoding from a DCEL is nearly mechanical (two chambers per
half-edge; σ₀ flips the endpoint bit, σ₁ follows `next`/`prev`, σ₂ follows
`twin`). The one genuinely new piece is **closure**: `closedMapSymbol`
requires a map with no boundary, so a periodic patch must first be wrapped
into its **torus quotient** using the translation periods that
`TilingLattice.periodicData` / `validatedPeriods` already detect. The
question forced here is how dcel should obtain the symbol machinery: depend
on `research-core`, or port the needed subset.

## Decision

**Port a self-contained subset of `research-core`'s `DelaneySymbols` engine
into dcel, and compute uniformity as the vertex-orbit count of the minimal
symbol of the torus quotient built from `TilingLattice` periods.** dcel does
not take a dependency on `research-core`.

The pipeline: detect periods (`TilingLattice`, heuristic, defect-tolerant) →
build the torus-quotient chamber map from the DCEL → `closedMapSymbol` →
`minimalSymbol` → count {σ₁,σ₂}-orbits. `minimalSymbol` divides out whatever
extra symmetry the chosen fundamental domain hid, so the answer does not
depend on the cell — the only heuristic left in the chain is period
detection, where it belongs (ADR-0015 already owns that caveat).

Scope of the port (stdlib-only, on the order of a few hundred lines):
`DSet`, `Orbit`, `DSymbol`, `collectOrbits`, `closedMapSymbol`,
`minimalSymbol`/`reduceOnce`, `canonicalKey`, `orbifoldSignature`,
`vertexConfig`, `isEuclidean`, `regularPolygonVertices`,
`classifyClosedMap`, and (second phase, see below)
`SymbolRenderer.develop` with its `apothem`/`circumradius`/`reflect`
helpers. New dcel code: the half-edge → chamber-involution encoder and the
torus-quotient construction. Ported files carry an attribution header naming
`research-core` and its Apache-2.0 licence (compatible with dcel's
Apache-2.0 OR MIT; same author).

## Beyond uniformity: what the same symbol carries

Uniformity motivates the port, but the minimal symbol is a complete
invariant of the tiling, so several other capabilities — today either
heuristic, approximate, or absent in dcel — fall out of the same object.
They are in scope of this decision so that each lands as a thin reader over
the one ported engine rather than as another bespoke subsystem:

- **Gonality and vertex configurations, exactly.** Face-transitivity
  classes are the {σ₀,σ₁}-orbits and vertex configurations come from
  `vertexConfig` (the σ₂σ₁ walk around a vertex, repeated `m₁₂/len` times
  where the symbol folds the vertex). This replaces
  `gonalitySampleInnerVertexIds`' radius-0 sampling — which inherits the
  hash-order-dependent representative choice and the boundary-signature
  key — with the definitional answer.
- **Edge-transitivity classes**, the {σ₀,σ₂}-orbits: a count dcel has no
  API for today, delivered by the same orbit walk.
- **The symmetry group itself.** `orbifoldSignature` reports tile, vertex
  and edge class counts plus the orbifold shape (orientability, cone
  orders, mirror boundaries) — i.e. the wallpaper group of the tiling.
  This supersedes `TilingSymmetry`'s patch-boundary rotational/reflectional
  order heuristics, which describe the finite patch's outline, not the
  tiling.
- **Tiling identity.** `canonicalKey` is a complete invariant for closed
  symbols: two tilings are isomorphic iff the keys of their minimal symbols
  are equal. This gives dcel true equality-up-to-isomorphism, deduplication
  and catalogue lookup (e.g. against the published Krötenheerdt keys) —
  something `TilingEquivalency`'s boundary comparison only approximates.
- **Symbol → geometry development** (second phase).
  `SymbolRenderer.develop` performs the barycentric development of complete
  faces straight from a symbol. Two uses: (a) a round-trip acceptance test
  for the bridge — DCEL patch → symbol → develop → compare with the
  original geometry; (b) the inverse constructor the editor lacks: build a
  `Tiling` from a catalogued symbol/key, i.e. "insert the 3.4.6.4 tiling"
  without hand-assembly. It is second-phase because uniformity does not
  depend on it and it is the one piece with geometric output that must be
  adapted to dcel's own `Tiling` builders rather than ported verbatim.

Each capability above ships only with tests against the corresponding
existing surface (`gonalityUnsafe`, `TilingSymmetry`, `TilingEquivalency`)
on fixtures where the current answer is known correct, plus the fixtures
where it is known wrong. Deprecation of the superseded surfaces is a
follow-up decision, as with `uniformityTree` below.

## Consequences

### Positive

- **Uniformity becomes exact and patch-independent** for periodic tilings —
  the definitionally correct number, not a radius-limited lower bound. The
  disabled 3.6.3.6 fixture becomes the acceptance test: expected 1, and it
  gets re-enabled.
- **One engine, several answers.** The same minimal symbol yields exact
  gonality, edge-transitivity classes, the orbifold/wallpaper signature,
  tiling identity via `canonicalKey`, and eventually symbol → tiling
  construction for the editor (see "Beyond uniformity" above), each as a
  thin reader over the one ported engine.
- **`TilingUniformity` and `TilingLattice` finally cooperate** instead of
  computing unrelated things side by side; the periodicity heuristic is
  quarantined in one place and its failure is reportable.
- The ported subset has **zero dependencies**, so the Scala.js target
  (ADR-0007) and the editor's bundle are unaffected; no coupling to
  `research-core`'s release cadence, whose development line deliberately
  moves ahead of its archived releases.
- For unit-edge regular-polygon tilings the minimal symbol's combinatorial
  orbit count *is* the geometric uniformity (an intrinsic closed all-360°
  map is a genuine flat tiling), so no separate geometric-symmetry check is
  needed in dcel's domain.

### Negative / risks

- **Deliberate code duplication.** The ported subset can drift from
  `research-core`'s. Mitigation: the attribution header records the source
  version; the subset is small, stable (it encodes 1980s mathematics, not a
  moving API), and covered by tests ported alongside it.
- **Uniformity of a non-periodic or boundary-dominated patch is
  ill-defined**, and this design makes that honest instead of silently
  wrong: when period detection fails, the exact pipeline has no answer and
  must say so (`Either`, ADR-0004) rather than return a number.
- The existing `uniformityTree` API answers a different (patch-relative)
  question and keeps its current, flawed semantics until a follow-up decides
  its fate; two "uniformity" surfaces coexist in the interim. This ADR does
  not remove it.
- Correctness now rests on two new pieces of dcel-owned code (chamber
  encoder, torus quotient). Both are cheaply cross-checkable: identity of
  `canonicalKey` across different fundamental domains, `isEuclidean`, the
  all-360° vertex check, and comparison against catalogued keys.

## Alternatives considered

- **Depend on the `research-core` artifact.** Rejected: no Scala.js artifact
  is published (JVM + Native only), dcel ships JS; and `research-core` is a
  research substrate whose public surface intentionally moves ahead of the
  pinned archives its verification repos consume — the wrong coupling for a
  published library. Revisit if `research-core` ever publishes a stable,
  cross-built core.
- **Fix the refinement heuristic** (compare patch interiors, chirality-aware
  keys, count stuck vertices). Rejected: repairs the symptoms but not the
  disease — the result would still be a patch-size-dependent lower bound
  with no relation to the symmetry group of the tiling.
- **Detect the symmetry group geometrically on the patch** (find all
  isometries, compute orbits). Rejected: amounts to re-deriving what the
  Delaney–Dress machinery already does combinatorially, with floating-point
  or exact-geometry isometry matching as an extra failure surface.
- **Move uniformity out of dcel** into a research repo. Rejected: the editor
  is a consumer (uniformity colouring, animations); the capability belongs
  in the library the editor builds on.

## Related

- ADR-0004 (`Either`-based errors — how period-detection failure surfaces).
- ADR-0007 (JS target — why the port must be dependency-free).
- ADR-0015 (largest contained parallelogon — the periodicity heuristic this
  pipeline consumes and confines).
- ADR-0018 (A068600 archive — the campaign that built the Delaney–Dress
  engine now being partially ported back).
