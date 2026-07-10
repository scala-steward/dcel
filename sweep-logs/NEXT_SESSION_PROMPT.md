# Next-session prompt (A068600 / strip-stacking)

GOAL. Crack OEIS A068600 — the complete counts of Krötenheerdt n-uniform tilings (edge-to-edge regular-polygon
tilof tilings. Our own sound generate-all D-symbol oracle is the AUTHORITY for n≤3.

WORKING DISCIPLINE (auto-memory — read MEMORY.md first; these are hard rules I keep being reminded of):
- RIGOROUS TEST-FIRST: before ANY probe/long run, unit-test every new/modified method AND every assumption the
  probe relies on (especially cross-engine KEY-SPACE compatibility, and that params reach the target n). A
  probe only produces artifacts on already-verified behavior; "I'll just run it to see" is the smell.
- INVESTIGATE BEFORE CONCLUDING: don't declare a lever dead / engine abandoned / cause found from incomplete or
  just-corrected data or a single example; verify the data, find the mechanism, generalize from validated cases.
- Safeguaings with EXACTLY n distinct vertex types): **11, 20, 39, 33, 15, 10, 7** for n=1..7 (0 for n≥8) — with an
  ORIGINAL, SOUND algorithm that runs in **under 1 week on this machine** (16 cores, 31 GiB). Branch
  `torus-map-enumeration`, JVM-only `generator/` subproject.

PLAY FAIR (standing, in auto-memory). Galebach's data + Wikipedia lists are a VALIDATION ORACLE ONLY — never a
source rded long runs: parallelism ≤12 of 16, `-Xmx12g -XX:+ExitOnOutOfMemoryError`, per-step `timeout`,
  durable gitignored `sweep-logs/` (not /tmp). No git push (user pushes). No Co-Authored-By trailer. CI = Java 17.

STATE (committed; HEAD ~3f6863e on torus-map-enumeration):
- n≤3 SOLVED + CERTIFIED by the rotation-agnostic generate-all oracle `DelaneySymbols.enumerateSymbols`
  (11/20/39, stable at maxSize 24=26; Conjecture R — every Krötenheerdt tiling has a rotation — DISCHARGED for
  n≤3). The reference audit fixed 2 n=3 transcription errors in `TilingReference` (oracle = authority).
- The symmetry GROWER is INCOMPLETE: true n=3 reach is 32/39 (NOT the previously-believed 36). Its misses lie
  entirely in the BANDED family (necessary, not sufficient). bounded-V reaches 5 of 7 grower-misses
  (grower-specific). All n≥4 grower coverage numbers are SUSPECT (measured against the error-laden reference).
- n≥4: the oracle is walled (chamber-tree exponential, ~5.8×/+2 maxSize; n=4 reached 31/33 at maxSize 28,
  rotation-free 0 ⇒ R partial, not discharged). Rejected/measured levers: curvature prune (sound, wall-neutral),
  closure-directed best-first (worse than DFS), orbifold-domain growth (not the fix — gap is not depth). See
  ADR-0034/0035/0036.

IMMEDIATE TASK: build the GENUINE STRIP-STACKING generator for the banded family, TEST-FIRST, render its
band-type catalogue for the user to inspect visually, THEN stack → enumerate → validate at n=3.

CORRECTED BAND MODEL (do NOT re-derive — I got it wrong twice, user corrected by eye; see ADR-0037):
- A band = a layer of unit regular polygons between two PROFILES. A profile = a periodic polyline of unit edges
  (ANY 30°-multiple turns: straight runs, 120°/60° zig-zags) + the partial vertex-fan consumed on each side at
  every vertex on it.
- Boundaries are NOT always straight: hexagon↔hexagon / hexagon↔triangle abut in a 120°/60° ZIG-ZAG of shared
  hexagon edges. A "straight" profile is not one polygon type — 3.6.3.6 has a straight line of ALTERNATING
  hexagon + triangle edges. So the model is polyline + per-vertex fans, with straight a special case.
- Two bands stack iff lower.topProfile == upper.bottomProfile (as polylines) AND at each shared vertex
  lower-fan + upper-fan is a valid 360° vertex.
- Build order: (1) `Profile` type; (2) fill-above-a-profile → band; (3) tests pinning the four canonical bands —
  square row, triangle row, 120° hexagon zig-zag row, 3.6.3.6 straight hex+triangle row — each
  `verifyCell`-consistent with the right profiles; (4) SVG catalogue of band types → SHOW THE USER; (5) stacking
  graph (profile-match) → cyclic stacks → banded tilings, deduped/keyed in the shared D-symbol space, validated
  at n=3 (reproduce the banded family incl. the grower's misses).

WHY THIS: the grower's misses are all anisotropic banded tilings (short in-band period, long across the stack),
which its compact-disk growth + shortest-period closure can't build. Strip-stacking is a fair, answer-blind,
1-D combinatorial enumerator targeting exactly that — and a path to n≥4 where 2-D growth scatters.

PITFALLS ALREADY PAID FOR (don't repeat):
- `KrotenheerdtTorusSearch.enumerateBanded` (fixed-Λ restricted to band-aligned lattices) is NOT a strip builder
  and emits the GEOMETRIC content key, DISJOINT from the oracle's D-symbol key (tested) ⇒ compare cross-engine
  by TYPE-SET, not key. It has an `onGeometry` callback for SVG. `StripStackProbe` is written but unrun.
- The straight-only fault detector in `CharacterizeBandedProbe` UNDERCOUNTED banded (zig-zag bands mislabeled
  non-banded) — needs a general periodic-polyline boundary test; re-characterize once the Profile type exists.

KEY FILES: `DelaneySymbols.scala` (oracle, canonicalKey, hasRotation, maxConeOrder); `KrotenheerdtTorusSearch.scala`
(fixed-Λ engine, `enumerateBanded` + onGeometry, `runLattice`, `classify`/`verifyContent` geometric key);
`KrotenheerdtTorusMapSearch.scala` (symmetry grower, `realizeCell`, `cellToOp`, `classifyClosedMap`, FaceZ).
SVG: `realizeCell`→faces→ custom emitter (see `MissedCellRenderProbe`/`StripStackProbe`), view via `inkscape
in.svg --export-type=png --export-filename=out.png`; reusable `shared/.../conversion/TilingSVG`. Reference
tilings in `generator/src/test/.../TilingReference.scala` (n=3 audit-corrected). Probes: ReferenceAuditProbe,
GrowerGapDiagnosisProbe, CharacterizeBandedProbe, MissedCellRenderProbe, StripStackProbe. ADRs 0034–0037.

DEFERRED: the n=4 R-discharge oracle run (`DischargeRProbe 4 30 12`, ~6h, safeguarded) when there's a window —
should push n=4 toward 33/33 and confirm rotation-free stays 0.

START BY: reading MEMORY.md + ADR-0037, then build the Profile/band generator test-first and SHOW me the band
catalogue SVGs before any stacking.
