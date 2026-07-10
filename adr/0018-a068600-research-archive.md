# ADR-0018: The A068600 research campaign — archive pointer

- **Status:** Accepted (campaign complete; the living project is
  [scala-tessella/uniform-tilings](https://github.com/scala-tessella/uniform-tilings)). 2026-07-10.

## What happened here

Between 2026-06 and 2026-07 this repository incubated, on the `torus-map-enumeration` branch, a research
campaign to replicate OEIS **A068600** (the Krötenheerdt n-uniform tilings, counts 11, 20, 39, 33, 15, 10, 7)
with an original, sound, fair algorithm. The campaign produced 26 ADRs (0018–0043 in the old numbering), a
`generator/` subproject of ~15 000 lines, and six engine families — a geometric grower, two fixed-lattice
torus engines, a bounded-cell dart assembler, a Delaney-symbol tree generator with an oriented-slice variant,
and a cylinder/profile transfer-matrix band engine — each of which hit a measured wall (covolume or
generation-tree complexity) that the trail documents.

The breakthrough came from a constraint-first pivot: a proved **12n bound** on minimal Delaney–Dress symbol
size, a fair top-down derivation of candidate vertex-type sets (even the polygon alphabet {3,4,6,8,12} is
derived), and a **SAT-based symbol assembler** that completed the entire sequence — including the
never-before-completed n = 4 = 33 and the n = 8 = 0 ceiling as 1617 exhaustive refutations — in about an
hour of compute. The same engine, generalized to type multisets, then reproduced every known cell of the
m-Archimedean × n-uniform table through row 8 and **corrected the published record**: (8-uniform,
2-Archimedean) = 258, not the 298 transcribed on Wikipedia.

## Where everything lives now

- **The full research trail** — all 26 ADRs, every engine, every measured dead end, and the complete commit
  history — is preserved at the annotated tag **`a068600-research-archive`** (the final state of the
  `torus-map-enumeration` branch). Nothing was lost; it is simply no longer checked out on main.
- **The living project** — the enumerator (`core` + `solver`), a compacted 8-ADR trail, the SVG atlas of all
  135 Krötenheerdt tilings, and the ongoing campaigns (rows 9+, the unknown k = 13 breakdown, DRAT
  certification) — is the sibling repository **uniform-tilings**.
- **What DCEL kept:** the `TilingLattice` period-detection improvement born from the campaign
  (`translationLattice` defect tolerance; `periodCandidates`/`validatedPeriods` exposing the validated
  period list for callers with stronger selection criteria) — merged to main alongside this pointer. The
  old experimental `generator/` subproject (superseded in full by uniform-tilings) was removed.

## Note on the append-only lifecycle

This repo's ADR policy says the trail is the point and ADRs are append-only. The campaign ADRs remain
append-only *at the tag*; this pointer replaces them on main so the index here stays about DCEL's own
architecture. Numbering 0019+ remains available for future DCEL decisions.
