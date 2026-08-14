package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.TilingLattice.translationLattice
import io.github.scala_tessella.dcel.delaney.DelaneySymbols.{DelaneyClassification, classifyTorusMapDetailed}
import io.github.scala_tessella.dcel.geometry.BigPoint
import io.github.scala_tessella.dcel.structure.{FaceId, HalfEdge, VertexId}

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
  * Failures surface as [[PeriodicityError]] (ADR-0004): a patch with no detectable lattice, a candidate
  * period that is not a genuine symmetry, or a patch too small to cover a fundamental domain has no exact
  * answer, and says so instead of returning a number.
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

  /** The torus quotient of a periodic patch: the chamber involutions `op(d) = (σ₀, σ₁, σ₂)` for chambers
    * `1..2·edges` (the input [[delaney.DelaneySymbols.closedMapSymbol]] expects), plus one representative
    * chamber per patch vertex and per patch inner face — the hooks that let orbit membership flow back from
    * the symbol to the concrete patch.
    */
  final private[dcel] case class TorusQuotient(
      op: Array[Array[Int]],
      vertexChamber: Map[VertexId, Int],
      faceChamber: Map[FaceId, Int]
  )

  extension (tiling: TilingDCEL)

    /** The tiling's torus quotient. Left when the patch is not recognisably periodic or the quotient is
      * inconsistent (a wrong period, or a patch not covering a full fundamental domain with margin).
      */
    private[dcel] def torusChamberMap(
        minOverlapFraction: Double = 0.25,
        maxDefectFraction: Double = 0.1
    ): Either[String, TorusQuotient] =
      if tiling.vertices.isEmpty then Left("Empty tiling: nothing to quotient")
      else
        tiling.translationLattice(minOverlapFraction, maxDefectFraction) match
          case None         => Left("No translation lattice detected: the patch is not recognisably periodic")
          case Some((v, w)) => tiling.torusChamberMapWithBasis(v, w)

    /** [[torusChamberMap]] with an explicitly supplied lattice basis — the quotient core, also usable to
      * probe individual period candidates.
      */
    private[dcel] def torusChamberMapWithBasis(
        v: BigPoint,
        w: BigPoint
    ): Either[String, TorusQuotient] =
      if tiling.vertices.isEmpty then Left("Empty tiling: nothing to quotient")
      else
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
                    case None    =>
                      // chamber (2·idx−1) is the flag at its edge's origin, so any inner half-edge
                      // leaving a vertex (resp. bounding a face) locates a chamber of its orbit
                      val vertexChamber = tiling.vertices
                        .flatMap: vertex =>
                          vertex.incidentEdgesUnsafe
                            .find(he => he.incidentFace.exists(_ != tiling.outerFace))
                            .map(he => vertex.id -> (2 * index(edgeKey(he)) - 1))
                        .toMap
                      val faceChamber   = tiling.innerFaces
                        .map(face => face.id -> (2 * index(edgeKey(face.halfEdgesUnsafe.head)) - 1))
                        .toMap
                      if vertexChamber.size != tiling.vertices.size then
                        Left("A vertex has no incident inner face: the patch is not a valid sample")
                      else Right(TorusQuotient(op, vertexChamber, faceChamber))

    /** The classified quotient with orbit membership carried back to the patch: the classification plus
      * per-vertex and per-face class assignments. Strict period validation is tried first: on a weld-free
      * patch a tolerantly-validated sublattice false period can reach the quotient and fail its guards, where
      * strict validation finds the genuine period; the tolerant fallback keeps patches with welded-defect
      * vertices classifiable.
      */
    private def classifiedQuotient(
        minOverlapFraction: Double,
        maxDefectFraction: Double
    ): Either[TilingError, (DelaneyClassification, Map[VertexId, Int], Map[FaceId, Int])] =
      def attempt(defectFraction: Double) =
        for
          quotient                             <-
            tiling.torusChamberMap(minOverlapFraction, defectFraction).left.map(PeriodicityError(_))
          (classification, minimal, toMinimal) <-
            classifyTorusMapDetailed(quotient.op).left.map(PeriodicityError(_))
        yield
          // orbs holds the 01-orbits first, so the 12-orbit position is offset by the face orbit count
          val faceOrbits    = classification.gonality
          val vertexClasses = quotient.vertexChamber.view
            .mapValues(chamber => minimal.orbitIndex(2)(toMinimal(chamber)) - faceOrbits)
            .toMap
          val faceClasses   = quotient.faceChamber.view
            .mapValues(chamber => minimal.orbitIndex(1)(toMinimal(chamber)))
            .toMap
          (classification, vertexClasses, faceClasses)
      attempt(0.0) match
        case Left(_) if maxDefectFraction > 0.0 => attempt(maxDefectFraction)
        case outcome                            => outcome

    /** The exact classification of the periodic tiling this patch samples, read off the minimal Delaney–Dress
      * symbol of its torus quotient: uniformity, gonality, vertex configurations, canonical key and orbifold
      * signature — all under the FULL symmetry group of the infinite tiling.
      */
    def delaneyClassification(
        minOverlapFraction: Double = 0.25,
        maxDefectFraction: Double = 0.1
    ): Either[TilingError, DelaneyClassification] =
      tiling.classifiedQuotient(minOverlapFraction, maxDefectFraction).map(_._1)

    /** The vertex transitivity class of EVERY vertex of the patch (boundary vertices included), as indices
      * `0 until uniformity`: two vertices share a class exactly when a symmetry of the infinite tiling maps
      * one onto the other. This is the exact replacement for the class grouping of
      * [[TilingDCEL.uniformityTree]], which classifies only a subset of the interior.
      */
    def delaneyVertexClasses(
        minOverlapFraction: Double = 0.25,
        maxDefectFraction: Double = 0.1
    ): Either[TilingError, Map[VertexId, Int]] =
      tiling.classifiedQuotient(minOverlapFraction, maxDefectFraction).map(_._2)

    /** The face transitivity class of every inner face of the patch, as indices `0 until gonality`: two faces
      * share a class exactly when a symmetry of the infinite tiling maps one onto the other.
      */
    def delaneyFaceClasses(
        minOverlapFraction: Double = 0.25,
        maxDefectFraction: Double = 0.1
    ): Either[TilingError, Map[FaceId, Int]] =
      tiling.classifiedQuotient(minOverlapFraction, maxDefectFraction).map(_._3)

    /** The exact uniformity (number of vertex transitivity classes) of the periodic tiling this patch
      * samples. Unlike [[TilingDCEL.uniformityTree]] — a patch-relative refinement — this is the definitional
      * quantity of the infinite tiling, independent of patch size.
      */
    def exactUniformity(
        minOverlapFraction: Double = 0.25,
        maxDefectFraction: Double = 0.1
    ): Either[TilingError, Int] =
      tiling.delaneyClassification(minOverlapFraction, maxDefectFraction).map(_.uniformity)
