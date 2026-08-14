package io.github.scala_tessella.dcel.delaney

import io.github.scala_tessella.ring_seq.RingSeq.bracelet
import spire.math.Rational

import scala.collection.mutable

/** Delaney–Dress symbols: the exact, coordinate-free machinery behind uniformity, gonality, symmetry and
  * tiling identity (ADR-0019).
  *
  * Ported subset of `research-core`'s `DelaneySymbols` engine (scala-tessella/research-core, commit
  * `aef8523`, 0.7.x line; Apache-2.0, same organisation — compatible with this library's Apache-2.0 OR MIT).
  * The enumeration/generator machinery is deliberately NOT ported; only the reader side a DCEL consumer
  * needs: build a symbol from a closed map, minimise it, and read orbits, keys and signatures off it.
  * Deviations from the source are limited to: `Frac` replaced by Spire's `Rational` (already a dcel
  * dependency), the `{3,4,6,8,12}` polygon-alphabet filter generalised to the exact 360° vertex-closure
  * check, `null`-sentinel micro-optimisations dropped, and the minimal-symbol reduction additionally exposing
  * the full-chamber → minimal-chamber class map ([[minimalSymbolWithMap]]).
  *
  * A 2D Delaney–Dress symbol is the barycentric subdivision of a tiling into **chambers** (vertex·edge·face
  * flags), quotiented by the symmetry group, carrying three involutions `σ₀, σ₁, σ₂` (cross the vertex / edge
  * / face of the flag) and `v`-values per orbit. The combinatorial dictionary:
  *   - a **01-orbit** (fix the face) is a TILE; its `m₀₁` = number of edges = polygon side-count.
  *   - a **12-orbit** (fix the vertex) is a VERTEX; its `m₁₂` = vertex degree.
  *   - a **02-orbit** (fix the edge) is an EDGE; `m₀₂` = 2 always (the 2-manifold condition).
  *
  * The MINIMAL symbol (quotient by the full symmetry group) is a complete invariant of the tiling; its
  * 12-orbit count is the uniformity, its 01-orbit count the gonality, its canonical key the tiling's identity
  * up to isomorphism.
  */
object DelaneySymbols:

  private inline val Dim = 2

  /** A Delaney set: `op(d)(i)` is the involution `σ_i` on chambers `1..size` (0 = "undefined/boundary"). */
  final class DSet(val op: Array[Array[Int]]):
    def size: Int                = op.length - 1 // index 0 unused (chambers are 1-based)
    def get(i: Int, d: Int): Int = if d >= 1 && d <= size then op(d)(i) else 0

  /** A `(i, j)`-orbit of chambers (a tile, vertex, or edge), with whether it is a "chain" (has a fixed point)
    * — `r` is its rotational length.
    */
  final case class Orbit(i: Int, j: Int, elements: Vector[Int], isChain: Boolean):
    def length: Int = elements.length
    def r: Int      = if isChain then length else (length + 1) / 2

  /** All `(i, j)`-orbits, where `j = i+1` (or `(0, 2)` for edges). */
  private def orbits(ds: DSet, i: Int, j: Int): Vector[Orbit] =
    val seen = Array.fill(ds.size + 1)(false)
    val out  = Vector.newBuilder[Orbit]
    var d    = 1
    while d <= ds.size do
      if !seen(d) then
        val orb     = mutable.ArrayBuffer(d)
        seen(d) = true
        var isChain = false
        var e       = d
        var k       = i
        var go      = true
        while go do
          val ek = ds.get(k, e)
          isChain = isChain || ek == e
          e = if ek == 0 then e else ek
          k = i + j - k
          if !seen(e) then { seen(e) = true; orb += e }
          if e == d && k == i then go = false
        out += Orbit(i, j, orb.toVector, isChain)
      d += 1
    out.result()

  private def partialOrientation(ds: DSet): Array[Int] =
    val ori = Array.fill(ds.size + 1)(0)
    if ds.size >= 1 then
      ori(1) = 1
      val q = mutable.Stack(1)
      while q.nonEmpty do
        val d = q.pop()
        var i = 0
        while i <= Dim do
          val di = ds.get(i, d)
          if di != 0 && ori(di) == 0 then { ori(di) = -ori(d); q.push(di) }
          i += 1
    ori

  private def isLoopless(ds: DSet): Boolean = (0 to Dim).forall: i =>
    (1 to ds.size).forall: d =>
      ds.get(i, d) != d

  private def isWeaklyOriented(ds: DSet): Boolean =
    val ori = partialOrientation(ds)
    (0 to Dim).forall: i =>
      (1 to ds.size).forall: d =>
        val di = ds.get(i, d)
        di == d || di == 0 || ori(di) != ori(d)

  private def isOriented(ds: DSet): Boolean = isLoopless(ds) && isWeaklyOriented(ds)

  /** A D-symbol: a D-set plus v-values per 01- and 12-orbit. `orbs` is `orbits(0,1) ++ orbits(1,2)`, in that
    * order; `orbitIndex(j)(d)` is the position in `orbs` of the `(j-1, j)`-orbit through `d`.
    */
  final class DSymbol(
      val dset: DSet,
      val orbs: Vector[Orbit],
      val orbitIndex: Array[Array[Int]],
      val vs: Array[Int]
  ):
    def size: Int                = dset.size
    def get(i: Int, d: Int): Int = dset.get(i, d)

    /** v-value of the (i,j)-orbit through `d` (1 for the two non-adjacent index pairs). */
    def v(i: Int, j: Int, d: Int): Int =
      if d < 1 || d > size then 0
      else if j == i + 1 then vs(orbitIndex(j)(d))
      else if i == j + 1 then vs(orbitIndex(i)(d))
      else if i != j && get(i, d) == get(j, d) then 2
      else 1

    private def rOf(i: Int, j: Int, d: Int): Int =
      if j == i + 1 then orbs(orbitIndex(j)(d)).r
      else if i == j + 1 then orbs(orbitIndex(i)(d)).r
      else if i != j && get(i, d) == get(j, d) then 1
      else 2

    /** `m_{i,i+1}` through `d` — the polygon side-count (i=0) or vertex degree (i=1). */
    def m(i: Int, j: Int, d: Int): Int = rOf(i, j, d) * v(i, j, d)

    /** Number of vertex transitivity classes (12-orbits). On a MINIMAL symbol this is the uniformity. */
    def vertexOrbitCount: Int = orbs.count(_.i == 1)

    /** Number of tile transitivity classes (01-orbits). On a MINIMAL symbol this is the gonality. */
    def faceOrbitCount: Int = orbs.count(_.i == 0)

  def collectOrbits(ds: DSet): (Vector[Orbit], Array[Array[Int]]) =
    val index = Array.fill(Dim + 1, ds.size + 1)(0)
    var built = Vector.empty[Orbit]
    var i     = 1
    while i <= Dim do
      for orb <- orbits(ds, i - 1, i) do
        built = built :+ orb
        for d <- orb.elements do index(i)(d) = built.length - 1
      i += 1
    (built, index)

  /** Wrap the three chamber involutions of a CLOSED 2-manifold map as a `v = 1` Delaney–Dress symbol. `op(d)`
    * holds `(σ₀, σ₁, σ₂)` for chamber `d`; chambers are 1-based, `op(0)` is unused, and every involution must
    * be total (a closed map has no boundary chambers). Because the map is the full barycentric subdivision
    * (no symmetry quotient), each 01-orbit's `m₀₁` is directly the polygon side-count and each 12-orbit's
    * `m₁₂` the vertex degree.
    */
  def closedMapSymbol(op: Array[Array[Int]]): DSymbol =
    val n             = op.length - 1
    val a             = Array.ofDim[Int](n + 1, Dim + 1)
    var d             = 1
    while d <= n do
      var i = 0
      while i <= Dim do { a(d)(i) = op(d)(i); i += 1 }
      d += 1
    val ds            = new DSet(a)
    val (orbs, index) = collectOrbits(ds)
    new DSymbol(ds, orbs, index, Array.fill(orbs.length)(1))

  extension (ds: DSymbol)
    /** True iff the symbol's curvature is zero — a euclidean (torus) tiling. */
    def isEuclidean: Boolean =
      val curvature = ds.orbs.foldLeft(Rational(-ds.size, 2)): (acc, orb) =>
        acc + Rational(if orb.isChain then 1 else 2, ds.v(orb.i, orb.j, orb.elements.head))
      curvature.isZero

  /** The cyclic sequence of polygon side-counts around the vertex whose 12-orbit contains chamber `d`: step
    * `d := σ₂(σ₁(d))` walks face-by-face around the vertex; each step's `m₀₁` is a polygon. The walk
    * traverses only the quotient fragment (`r₁₂` faces); the genuine vertex degree is `m₁₂ = r₁₂·v₁₂`, so the
    * geometric vertex is the fragment repeated to length `m₁₂` (the symbol's symmetry folds the vertex).
    */
  def vertexConfig(ds: DSymbol, d: Int): Option[List[Int]] =
    val frag = mutable.ArrayBuffer.empty[Int]
    var cur  = d
    var go   = true
    while go do
      frag += ds.m(0, 1, cur)
      val nxt = ds.get(2, ds.get(1, cur))
      cur = if nxt == 0 then cur else nxt
      if cur == d then go = false
      else if frag.length > 24 then return None // runaway guard
    val m12  = ds.m(1, 2, d)
    if frag.isEmpty || m12 % frag.length != 0 then None
    else Some(List.fill(m12 / frag.length)(frag.toList).flatten)

  /** True iff the interior angles of regular polygons with these side-counts sum to exactly 360°. */
  private def closesTo360(config: List[Int]): Boolean =
    val interiorAngleTurns = config.foldLeft(Rational.zero)((acc, p) => acc + Rational(p - 2, p))
    interiorAngleTurns == Rational(2)

  /** The vertex configurations (one per 12-orbit, bracelet-canonical) iff every vertex closes to exactly 360°
    * — the intrinsic flatness check that replaces any geometric overlap test.
    */
  def completeVertexConfigs(ds: DSymbol): Option[List[List[Int]]] =
    val configs = ds.orbs.filter(_.i == 1).map(o => vertexConfig(ds, o.elements.head))
    if configs.forall(_.exists(closesTo360)) then Some(configs.flatten.map(_.bracelet.toList).toList)
    else None

  /** The MINIMAL (maximal-symmetry) Delaney–Dress symbol covered by `ds`: quotient by a proper m-preserving
    * op-congruence, iterated to a fixed point. Every torus cover of one tiling reduces to the SAME minimal
    * symbol (Delaney–Dress: it is a complete invariant), so it is the canonical identity for dedup, and its
    * vertex orbits are the uniformity — independent of the chosen cell.
    */
  extension (ds: DSymbol)
    def minimalSymbol: DSymbol = ds.minimalSymbolWithMap._1

    /** [[minimalSymbol]] plus the chamber class map: `map(d)` is the minimal-symbol chamber that original
      * chamber `d` folds onto (index 0 unused). The map is what lets a caller carry per-chamber data — e.g.
      * which vertex or face of a concrete map a chamber belongs to — down to the orbits of the minimal
      * symbol.
      */
    def minimalSymbolWithMap: (DSymbol, Array[Int]) =
      var cur  = ds
      var map  = Array.tabulate(ds.size + 1)(identity)
      var step = reduceOnce(cur)
      while step.isDefined do
        val (next, cls) = step.get
        map = map.map(cls)
        cur = next
        step = reduceOnce(cur)
      (cur, map)

  /** One quotient step: the finest m-constant op-congruence identifying chamber 1 with some `d0` (with its
    * chamber class map), or `None` if the symbol is already minimal.
    */
  private def reduceOnce(ds: DSymbol): Option[(DSymbol, Array[Int])] =
    val n  = ds.size
    var d0 = 2
    while d0 <= n do
      val parent                         = Array.tabulate(n + 1)(identity)
      def find(x: Int): Int              =
        var r = x
        while parent(r) != r do r = parent(r)
        var c = x
        while parent(c) != c do { val p = parent(c); parent(c) = r; c = p }
        r
      def union(a: Int, b: Int): Boolean =
        val (ra, rb) = (find(a), find(b))
        if ra == rb then false else { parent(ra) = rb; true }
      val queue                          = mutable.Queue((1, d0))
      union(1, d0): Unit
      while queue.nonEmpty do
        val (a, b) = queue.dequeue()
        var i      = 0
        while i <= Dim do
          val (ai, bi) = (ds.get(i, a), ds.get(i, b))
          if union(ai, bi) then queue.enqueue((ai, bi))
          i += 1
      // seed union(1, d0) always merges two classes — the congruence is always proper, no count guard
      val m01                            = Array.fill(n + 1)(-1)
      val m12                            = Array.fill(n + 1)(-1)
      var ok                             = true
      var d                              = 1
      while d <= n && ok do
        val rep = find(d)
        if m01(rep) < 0 then { m01(rep) = ds.m(0, 1, d); m12(rep) = ds.m(1, 2, d) }
        else if m01(rep) != ds.m(0, 1, d) || m12(rep) != ds.m(1, 2, d) then ok = false
        d += 1
      if ok then return Some(quotient(ds, Array.tabulate(n + 1)(find)))
      d0 += 1
    None

  /** Quotient `ds` by the class map `cls` (an m-constant op-congruence), together with the chamber map
    * `original chamber -> quotient chamber`. v-values are recomputed so the polygon side-counts and vertex
    * degrees (`m₀₁`, `m₁₂`) are preserved: `v_new = m_original / r_new`.
    */
  private def quotient(ds: DSymbol, cls: Array[Int]): (DSymbol, Array[Int]) =
    val n             = ds.size
    val label         = mutable.LinkedHashMap.empty[Int, Int] // class rep -> new 1-based label
    val repOf         = mutable.ArrayBuffer(0)                // new label -> a representative original chamber
    var d             = 1
    while d <= n do
      val r = cls(d)
      if !label.contains(r) then { label(r) = label.size + 1; repOf += r }
      d += 1
    val c             = label.size
    val a             = Array.ofDim[Int](c + 1, Dim + 1)
    var lab           = 1
    while lab <= c do
      val orig = repOf(lab)
      var i    = 0
      while i <= Dim do { a(lab)(i) = label(cls(ds.get(i, orig))); i += 1 }
      lab += 1
    val qds           = new DSet(a)
    val (orbs, index) = collectOrbits(qds)
    val vs            = Array.tabulate(orbs.length): k =>
      val orb   = orbs(k)
      val mOrig =
        if orb.i == 0 then ds.m(0, 1, repOf(orb.elements.head)) else ds.m(1, 2, repOf(orb.elements.head))
      mOrig / orb.r
    val chamberMap    = Array.tabulate(n + 1)(d => if d == 0 then 0 else label(cls(d)))
    (new DSymbol(qds, orbs, index, vs), chamberMap)

  /** A canonical key for a CLOSED symbol: the lexicographically minimal BFS-renumbered trace of the three
    * involutions plus `(m₀₁, m₁₂)` per chamber, over every start chamber. Two symbols are isomorphic iff
    * their keys are equal.
    */
  extension (ds: DSymbol)
    def canonicalKey: String =
      val n = ds.size
      (1 to n)
        .map: s =>
          val o2n   = Array.fill(n + 1)(0)
          val n2o   = Array.fill(n + 1)(0)
          o2n(s) = 1
          n2o(1) = s
          var next  = 2
          val trace = new StringBuilder
          var d     = 1
          while d <= n do
            val orig = n2o(d)
            var i    = 0
            while i <= Dim do
              val ei = ds.get(i, orig)
              if o2n(ei) == 0 then { o2n(ei) = next; n2o(next) = ei; next += 1 }
              trace.append(o2n(ei)).append(','): Unit
              i += 1
            trace.append(ds.m(0, 1, orig)).append('|').append(ds.m(1, 2, orig)).append(';'): Unit
            d += 1
          trace.toString
        .min

  /** The orbifold "shape" of a minimal symbol: `D` = chambers, `t/v/e` = number of tile / vertex / edge
    * orbits, orientability, whether it has mirror boundaries (loops), and the cone/corner orders (orbit
    * v-values > 1 = rotation orders). Two minimal symbols sharing this signature are triangulations of the
    * same euclidean orbifold.
    */
  extension (ds: DSymbol)
    def orbifoldSignature: String =
      val o01     = orbits(ds.dset, 0, 1)
      val o12     = orbits(ds.dset, 1, 2)
      val o02     = orbits(ds.dset, 0, 2)
      val ori     = isOriented(ds.dset)
      val mir     = !isLoopless(ds.dset)
      val cones01 = o01.toList.map(o => ds.v(0, 1, o.elements.head)).filter(_ > 1).sorted
      val cones12 = o12.toList.map(o => ds.v(1, 2, o.elements.head)).filter(_ > 1).sorted
      s"D=${ds.size} t=${o01.length} v=${o12.length} e=${o02.length} ori=$ori mir=$mir " +
        s"cone-tile=[${cones01.mkString(",")}] cone-vert=[${cones12.mkString(",")}]"

  /** The exact classification of a torus tiling read off its minimal Delaney–Dress symbol.
    *
    * @param uniformity
    *   number of vertex transitivity classes under the FULL symmetry group of the tiling
    * @param gonality
    *   number of tile transitivity classes
    * @param vertexConfigs
    *   the vertex configurations, one per vertex class, bracelet-canonical
    * @param canonicalKey
    *   the minimal symbol's canonical key — the tiling's identity up to isomorphism
    * @param orbifoldSignature
    *   the euclidean orbifold shape (orbit counts, orientability, mirrors, cone orders)
    * @param chambers
    *   chamber count of the minimal symbol
    */
  final case class DelaneyClassification(
      uniformity: Int,
      gonality: Int,
      vertexConfigs: List[List[Int]],
      canonicalKey: String,
      orbifoldSignature: String,
      chambers: Int
  )

  /** Classify a CLOSED map (given its chamber involutions) as a flat torus tiling. Euclidicity (curvature 0)
    * plus every vertex closing to exactly 360° make the map a genuine flat tiling by the developing-map
    * theorem — no geometric overlap test is needed. The classification is read off the MINIMAL symbol, so
    * every count is under the full symmetry group of the tiling, independent of the chosen cell.
    */
  def classifyTorusMap(op: Array[Array[Int]]): Either[String, DelaneyClassification] =
    classifyTorusMapDetailed(op).map(_._1)

  /** [[classifyTorusMap]] plus the data needed to carry per-chamber assignments to the minimal symbol's
    * orbits: the minimal symbol itself and the chamber class map `full chamber -> minimal chamber` (see
    * [[minimalSymbolWithMap]]).
    */
  def classifyTorusMapDetailed(
      op: Array[Array[Int]]
  ): Either[String, (DelaneyClassification, DSymbol, Array[Int])] =
    val full = closedMapSymbol(op)
    if !full.isEuclidean then Left("Symbol is not euclidean (curvature ≠ 0): not a torus quotient")
    else if completeVertexConfigs(full).isEmpty then
      Left("A vertex configuration does not close to exactly 360°")
    else
      val (minimal, chamberMap) = full.minimalSymbolWithMap
      completeVertexConfigs(minimal)
        .toRight("Minimal symbol lost vertex closure (internal error)")
        .map: configs =>
          val classification = DelaneyClassification(
            uniformity = minimal.vertexOrbitCount,
            gonality = minimal.faceOrbitCount,
            vertexConfigs = configs,
            canonicalKey = minimal.canonicalKey,
            orbifoldSignature = minimal.orbifoldSignature,
            chambers = minimal.size
          )
          (classification, minimal, chamberMap)
