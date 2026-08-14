package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.TilingLattice.translationLattice
import io.github.scala_tessella.dcel.delaney.DelaneySymbols.{DelaneyClassification, classifyTorusMap}
import io.github.scala_tessella.dcel.geometry.BigPoint
import io.github.scala_tessella.dcel.structure.HalfEdge

import scala.collection.mutable

/** The bridge from a periodic DCEL patch to its Delaney–Dress symbol (ADR-0019): exact uniformity, gonality,
  * symmetry and tiling identity.
  *
  * Pipeline: detect the translation lattice ([[TilingLattice.translationLattice]], the one heuristic step) →
  * quotient the patch by the lattice into a CLOSED torus map (every vertex, edge and face keyed by its
  * position modulo the lattice) → encode its barycentric chambers (two per half-edge; `σ₀` swaps the
  * endpoint, `σ₁` follows `next`/`prev` within the face, `σ₂` crosses to the twin) → classify via
  * [[delaney.DelaneySymbols.classifyTorusMap]]. The minimal symbol divides out whatever extra symmetry the
  * chosen cell hid, so the result does not depend on which fundamental domain the lattice detector anchored.
  *
  * Errors are reported as messages for now (prototype); wrapping them into the [[TilingError]] ADT (ADR-0004)
  * is a follow-up.
  */
object TilingDelaney:

  /** Quotient keys round to this scale: coordinates carry ~1e-12 float error (ADR-0009), and genuinely
    * distinct fractional lattice coordinates / edge directions differ by far more than 1e-6.
    */
  private val SCALE = 6

  private type Rounded2 = (BigDecimal, BigDecimal)

  /** A quotient half-edge: the origin vertex's position modulo the lattice, plus the edge direction (both
    * translation-invariant).
    */
  private type QuotientEdgeKey = (Rounded2, Rounded2)

  extension (tiling: TilingDCEL)

    /** The chamber involutions of the tiling's torus quotient: `op(d) = (σ₀, σ₁, σ₂)` for chambers
      * `1..2·edges`, the input [[delaney.DelaneySymbols.closedMapSymbol]] expects. Left when the patch is not
      * recognisably periodic or the quotient is inconsistent (a wrong period, or a patch not covering a full
      * fundamental domain with margin).
      */
    private[dcel] def torusChamberMap: Either[String, Array[Array[Int]]] =
      if tiling.vertices.isEmpty then Left("Empty tiling: nothing to quotient")
      else
        tiling.translationLattice() match
          case None         => Left("No translation lattice detected: the patch is not recognisably periodic")
          case Some((v, w)) =>
            val det    = v.cross(w)
            val anchor = tiling.vertices.head.coords

            def rounded(x: BigDecimal): BigDecimal =
              x.setScale(SCALE, BigDecimal.RoundingMode.HALF_UP)

            def fractional(x: BigDecimal): BigDecimal =
              val r = rounded(x - x.setScale(0, BigDecimal.RoundingMode.FLOOR))
              if r == BigDecimal(1) then rounded(BigDecimal(0)) else r

            // position modulo the lattice, as rounded fractional lattice coordinates
            def vertexKey(p: BigPoint): Rounded2 =
              val d = p - anchor
              (fractional((d.x * w.y - w.x * d.y) / det), fractional((v.x * d.y - d.x * v.y) / det))

            def edgeKey(halfEdge: HalfEdge): QuotientEdgeKey =
              val p = halfEdge.origin.coords
              val q = halfEdge.destinationUnsafe.coords
              (vertexKey(p), (rounded(q.x - p.x), rounded(q.y - p.y)))

            val innerHalfEdges: List[HalfEdge] = tiling.innerFaces.flatMap(_.halfEdgesUnsafe)

            // one index per quotient edge, plus a representative's endpoints for the geometric twin lookup
            val index          = mutable.LinkedHashMap.empty[QuotientEdgeKey, Int]
            val representative = mutable.ArrayBuffer.empty[HalfEdge]
            innerHalfEdges.foreach: halfEdge =>
              val key = edgeKey(halfEdge)
              if !index.contains(key) then
                index(key) = index.size + 1
                representative += halfEdge

            val size   = index.size
            val nextOf = Array.fill(size + 1)(0)
            val prevOf = Array.fill(size + 1)(0)
            val twinOf = Array.fill(size + 1)(0)

            def link(map: Array[Int], from: Int, to: Int): Option[String] =
              if map(from) == 0 then { map(from) = to; None }
              else if map(from) == to then None
              else
                Some(
                  s"Inconsistent quotient: two representatives of the same edge class disagree " +
                    s"(class $from maps to both ${map(from)} and $to) — the detected period is not genuine"
                )

            val inconsistency: Option[String] =
              innerHalfEdges.view
                .flatMap: halfEdge =>
                  val here  = index(edgeKey(halfEdge))
                  val after = index(edgeKey(halfEdge.next.get))
                  link(nextOf, here, after).orElse(link(prevOf, after, here))
                .headOption

            inconsistency match
              case Some(error) => Left(error)
              case None        =>
                // the twin's class is purely geometric: same edge seen from the other endpoint
                val missingTwin: Option[String] = (1 to size).view
                  .flatMap: idx =>
                    val rep     = representative(idx - 1)
                    val p       = rep.origin.coords
                    val q       = rep.destinationUnsafe.coords
                    val twinKey = (vertexKey(q), (rounded(p.x - q.x), rounded(p.y - q.y)))
                    index.get(twinKey) match
                      case Some(twinIdx) => twinOf(idx) = twinIdx; None
                      case None          =>
                        Some(
                          s"Quotient edge class $idx has no reversed counterpart: the patch does not " +
                            "cover a full fundamental domain of the detected lattice"
                        )
                  .headOption

                missingTwin match
                  case Some(error) => Left(error)
                  case None        =>
                    val incomplete = (1 to size).find(idx => nextOf(idx) == 0 || prevOf(idx) == 0)
                    if incomplete.isDefined then
                      Left(s"Quotient edge class ${incomplete.get} has no next/prev representative")
                    else
                      // chambers: 2h-1 = (edge h, origin flag), 2h = (edge h, destination flag)
                      val op     = Array.ofDim[Int](2 * size + 1, 3)
                      (1 to size).foreach: h =>
                        val originChamber      = 2 * h - 1
                        val destinationChamber = 2 * h
                        op(originChamber)(0) = destinationChamber
                        op(destinationChamber)(0) = originChamber
                        op(originChamber)(1) = 2 * prevOf(h)
                        op(destinationChamber)(1) = 2 * nextOf(h) - 1
                        op(originChamber)(2) = 2 * twinOf(h)
                        op(destinationChamber)(2) = 2 * twinOf(h) - 1
                      val broken = (1 to 2 * size).find(c => (0 to 2).exists(i => op(op(c)(i))(i) != c))
                      broken match
                        case Some(c) => Left(s"Chamber map is not involutive at chamber $c")
                        case None    => Right(op)

    /** The exact classification of the periodic tiling this patch samples, read off the minimal Delaney–Dress
      * symbol of its torus quotient: uniformity, gonality, vertex configurations, canonical key and orbifold
      * signature — all under the FULL symmetry group of the infinite tiling.
      */
    def delaneyClassification: Either[String, DelaneyClassification] =
      tiling.torusChamberMap.flatMap(classifyTorusMap)

    /** The exact uniformity (number of vertex transitivity classes) of the periodic tiling this patch
      * samples. Unlike [[TilingDCEL.uniformityTree]] — a patch-relative refinement — this is the definitional
      * quantity of the infinite tiling, independent of patch size.
      */
    def exactUniformity: Either[String, Int] =
      tiling.delaneyClassification.map(_.uniformity)
