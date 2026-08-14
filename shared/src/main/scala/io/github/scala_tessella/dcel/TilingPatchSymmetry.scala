package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.geometry.BigDecimalGeometry.ACCURACY
import io.github.scala_tessella.dcel.geometry.BigPoint
import io.github.scala_tessella.dcel.geometry.BigPoint.*
import io.github.scala_tessella.dcel.structure.{Vertex, VertexId}

import scala.collection.mutable

/** The isometry group of the FINITE PATCH, found geometrically, and the vertex orbits under it (ADR-0020).
  *
  * This answers a question that is well posed for every patch, periodic or not: which vertices are carried
  * onto each other by a rigid motion of the plane that maps the patch onto itself. Contrast the two
  * neighbouring surfaces:
  *   - [[TilingDelaney.delaneyVertexClasses]] — orbits under the symmetry group of the INFINITE tiling the
  *     patch samples. Exact and patch-independent, but undefined when no lattice is detectable.
  *   - [[TilingSymmetry]] — the rotational order and reflection axes of the patch, read off its outer
  *     boundary. Same object as here, but it reports the axes rather than the induced vertex permutations, so
  *     it cannot produce orbits.
  *
  * Every isometry of a bounded figure fixes its centroid, and the group is therefore finite and either cyclic
  * or dihedral (Leonardo's theorem) — see [[PatchSymmetryGroup]]. That bound is what makes the search cheap:
  * a symmetry must permute the vertices at maximal distance from the centroid, so fixing one such reference
  * vertex leaves at most one rotation and one reflection per candidate image, and each is confirmed by
  * applying it to the whole patch.
  *
  * Matching is tolerance-based, not exact: coordinates are `BigDecimal`-typed but produced through `Double`
  * trigonometry and so carry ~1e-12 of error (ADR-0009), the same reason [[TilingDelaney]] keys its quotient
  * on rounded coordinates.
  */
object TilingPatchSymmetry:

  /** The symmetry group of a bounded patch: `rotationOrder` rotations about the centroid, and either no
    * reflections (cyclic, `Cn`) or the same number of them (dihedral, `Dn`). `C1` is the trivial group — the
    * patch has no symmetry beyond the identity, and every vertex is its own orbit.
    */
  case class PatchSymmetryGroup(rotationOrder: Int, hasReflections: Boolean):

    /** Total number of elements, the identity included. */
    def order: Int =
      if hasReflections then rotationOrder * 2 else rotationOrder

    /** True when the identity is the only symmetry. */
    def isTrivial: Boolean =
      order == 1

    /** Schoenflies-style name, `C3` or `D6`. */
    def name: String =
      s"${if hasReflections then "D" else "C"}$rotationOrder"

  /** A candidate symmetry as a linear part about the fixed centroid: `g(p) = centre + M·(p − centre)`, where
    * `M` is the rotation `[[cos, −sin], [sin, cos]]` or, when `reverses`, the reflection
    * `[[cos, sin], [sin, −cos]]`.
    */
  final private case class PatchIsometry(
      centre: BigPoint,
      cos: BigDecimal,
      sin: BigDecimal,
      reverses: Boolean
  ):

    def apply(point: BigPoint): BigPoint =
      val d     = point - centre
      val image =
        if reverses then BigPoint(cos * d.x + sin * d.y, sin * d.x - cos * d.y)
        else BigPoint(cos * d.x - sin * d.y, sin * d.x + cos * d.y)
      centre + image

  extension (tiling: TilingDCEL)

    /** Locates a vertex by position, within `accuracy`. The grid mirrors `BigPoint.hasNoAlmostEqualPoints`:
      * cells of side `accuracy`, so a match is always in the queried cell or one of its eight neighbours.
      */
    private def vertexLocator(accuracy: Double): BigPoint => Option[VertexId] =
      val side = BigDecimal(accuracy)

      def cellOf(point: BigPoint): (Long, Long) =
        (
          (point.x / side).setScale(0, BigDecimal.RoundingMode.FLOOR).toLong,
          (point.y / side).setScale(0, BigDecimal.RoundingMode.FLOOR).toLong
        )

      val grid = mutable.HashMap.empty[(Long, Long), List[Vertex]]
      tiling.vertices.foreach: vertex =>
        val cell = cellOf(vertex.coords)
        grid.update(cell, vertex :: grid.getOrElse(cell, Nil))

      point =>
        val (cx, cy)   = cellOf(point)
        val candidates =
          for
            i      <- -1 to 1
            j      <- -1 to 1
            vertex <- grid.getOrElse((cx + i, cy + j), Nil)
            if vertex.coords.almostEquals(point, accuracy)
          yield vertex.id
        candidates.headOption

    /** The undirected edge set as unordered id pairs, the smaller id first. */
    private def edgeKeys: Set[(VertexId, VertexId)] =
      tiling.halfEdges.view
        .flatMap: halfEdge =>
          halfEdge.maybeId
        .map: (from, to) =>
          if from.value <= to.value then (from, to) else (to, from)
        .toSet

    /** Every symmetry of the patch, as the vertex permutation it induces, paired with its orientation
      * behaviour. The identity is always present, so the result is never empty for a non-empty patch.
      *
      * A candidate survives only if it maps the vertex set ONTO itself and carries every edge to an edge — an
      * isometry preserving both determines the planar embedding, so faces follow without a separate check.
      */
    private def patchSymmetries(accuracy: Double): List[(Boolean, Map[VertexId, VertexId])] =
      val vertices = tiling.vertices
      if vertices.isEmpty then Nil
      else
        val centre = vertices.map(_.coords).centroid

        def radiusSqOf(vertex: Vertex): BigDecimal =
          val spoke = vertex.coords - centre
          spoke.dot(spoke)

        val radiusSq  = vertices.map(radiusSqOf)
        val maxSq     = radiusSq.max
        // Squared lengths inherit ~1e-12 of coordinate error amplified by the radius; the tolerance scales
        // with the patch so it stays far below the spacing of genuinely distinct radii in a unit-edge tiling.
        val tolerance = BigDecimal(accuracy) * (maxSq + 1)

        if maxSq <= tolerance then Nil // every vertex sits on the centroid: degenerate, nothing to say
        else
          val locate    = tiling.vertexLocator(accuracy)
          val edges     = tiling.edgeKeys
          val outermost = vertices.zip(radiusSq).collect:
            case (vertex, distanceSq) if (maxSq - distanceSq).abs <= tolerance => vertex
          val reference = outermost.head.coords - centre
          val lengthSq  = reference.dot(reference)

          // A symmetry permutes the outermost vertices, so it is pinned by the image of the reference one:
          // at most one rotation and one reflection per candidate image.
          val candidates =
            for
              target          <- outermost
              image            = target.coords - centre
              reverses        <- List(false, true)
              (rawCos, rawSin) =
                if reverses then
                  (
                    (reference.x * image.x - reference.y * image.y) / lengthSq,
                    (reference.y * image.x + reference.x * image.y) / lengthSq
                  )
                else (reference.dot(image) / lengthSq, reference.cross(image) / lengthSq)
              normSq           = rawCos * rawCos + rawSin * rawSin
              if normSq > 0
              // `reference` and `image` agree in length only to within coordinate error, so the raw matrix
              // is off unit determinant by ~1e-12; renormalising keeps that from growing with the radius.
              unit             = BigDecimal(1.0 / Math.sqrt(normSq.toDouble))
            yield PatchIsometry(centre, rawCos * unit, rawSin * unit, reverses)

          candidates.flatMap: isometry =>
            val mapped =
              vertices.map: vertex =>
                locate(isometry(vertex.coords)).map(vertex.id -> _)
            if mapped.exists(_.isEmpty) then None
            else
              val permutation    = mapped.flatten.toMap
              val preservesEdges =
                permutation.size == vertices.size &&
                  permutation.values.toSet.size == vertices.size &&
                  edges.forall: (from, to) =>
                    val a = permutation(from)
                    val b = permutation(to)
                    edges.contains(if a.value <= b.value then (a, b) else (b, a))
              Option.when(preservesEdges)(isometry.reverses -> permutation)

    /** The symmetry group of the patch as drawn — how many rotations about its centroid map it onto itself,
      * and whether it also has reflections. Always defined; a patch with no symmetry reports `C1`.
      */
    def patchSymmetryGroup(accuracy: Double = ACCURACY): PatchSymmetryGroup =
      val symmetries = tiling.patchSymmetries(accuracy)
      if symmetries.isEmpty then PatchSymmetryGroup(1, hasReflections = false)
      else
        val (reversing, preserving) = symmetries.partition(_._1)
        PatchSymmetryGroup(preserving.size, reversing.nonEmpty)

    /** The orbit of EVERY vertex under the patch's own symmetry group, as indices `0 until orbitCount`: two
      * vertices share an index exactly when some rigid motion mapping the patch onto itself carries one to
      * the other. Indices are assigned in order of each orbit's lowest `VertexId`, so they are stable across
      * runs.
      *
      * Unlike [[TilingDelaney.delaneyVertexClasses]] this never fails and never leaves a vertex out — but it
      * describes the finite patch, not the tiling it samples. On a patch with no symmetry every vertex is its
      * own orbit, which is the honest answer rather than a useful colouring; callers wanting to say so can
      * ask [[patchSymmetryGroup]] first.
      */
    def patchVertexClasses(accuracy: Double = ACCURACY): Map[VertexId, Int] =
      val vertices = tiling.vertices.map(_.id)
      val parent   = mutable.HashMap.from(vertices.map(id => id -> id))

      def root(id: VertexId): VertexId =
        var current = id
        while parent(current) != current do
          parent.update(current, parent(parent(current)))
          current = parent(current)
        current

      tiling.patchSymmetries(accuracy).foreach: (_, permutation) =>
        permutation.foreach: (from, to) =>
          val (a, b) = (root(from), root(to))
          if a != b then parent.update(a, b)

      vertices
        .groupBy(root)
        .values
        .toList
        .sortBy(_.map(_.value).min)
        .zipWithIndex
        .flatMap: (ids, index) =>
          ids.map(_ -> index)
        .toMap
