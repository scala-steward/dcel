package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.TilingDelaney.delaneyVertexClasses
import io.github.scala_tessella.dcel.TilingPatchSymmetry.*
import io.github.scala_tessella.dcel.TilingSymmetry.rotationalSymmetryOrder
import io.github.scala_tessella.dcel.geometry.RegularPolygon
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The patch symmetry group and its vertex orbits (ADR-0020) — the answer available for every patch,
  * including the ones [[TilingDelaney]] cannot classify.
  */
class TilingPatchSymmetrySpec extends AnyFlatSpec with Matchers with TilingTestHelpers:

  /** A hexagon with a triangle grown on one edge: the only surviving symmetry is the mirror through that
    * edge's midpoint.
    */
  private def hexagonWithTriangle: Tiling =
    hexagon.maybeAddRegularPolygonToBoundary(V1, RegularPolygon(3)).value

  /** The same patch with a square on a second edge, placed so no mirror or rotation survives. */
  private def asymmetricPatch: Tiling =
    hexagonWithTriangle.maybeAddRegularPolygonToBoundary(V3, RegularPolygon(4)).value

  behavior of "TilingPatchSymmetry.patchSymmetryGroup()"

  it should "find the full dihedral group of a single regular polygon" in:
    allAssert(
      triangle.patchSymmetryGroup().name shouldBe "D3",
      square.patchSymmetryGroup().name shouldBe "D4",
      hexagon.patchSymmetryGroup().name shouldBe "D6",
      dodecagon.patchSymmetryGroup().name shouldBe "D12"
    )

  it should "count both rotations and reflections in the group order" in:
    allAssert(
      square.patchSymmetryGroup().order shouldBe 8,
      square.patchSymmetryGroup().hasReflections shouldBe true,
      square.patchSymmetryGroup().isTrivial shouldBe false
    )

  it should "find only the half turn and the two diagonal mirrors of a rhombus" in:
    rhombus.patchSymmetryGroup().name shouldBe "D2"

  it should "report the trivial group for the empty tiling" in:
    allAssert(
      emptyTiling.patchSymmetryGroup().name shouldBe "C1",
      emptyTiling.patchSymmetryGroup().isTrivial shouldBe true
    )

  it should "agree with the boundary-based rotational order of TilingSymmetry" in:
    val tilings = List(triangle, square, rhombus, hexagon, dodecagon)
    allAssert(
      tilings.map(_.patchSymmetryGroup().rotationOrder) shouldBe tilings.map(_.rotationalSymmetryOrder)
    )

  it should "keep the single mirror of a hexagon grown by one triangle" in:
    hexagonWithTriangle.patchSymmetryGroup().name shouldBe "D1"

  behavior of "TilingPatchSymmetry.patchVertexClasses()"

  it should "put every vertex of a regular polygon in one orbit" in:
    allAssert(
      triangle.patchVertexClasses().values.toSet shouldBe Set(0),
      square.patchVertexClasses().values.toSet shouldBe Set(0),
      hexagon.patchVertexClasses().values.toSet shouldBe Set(0),
      dodecagon.patchVertexClasses().values.toSet shouldBe Set(0)
    )

  it should "separate the acute from the obtuse corners of a rhombus" in:
    val classes = rhombus.patchVertexClasses()
    allAssert(
      classes.values.toSet shouldBe Set(0, 1),
      classes.groupBy(_._2).values.map(_.size).toList shouldBe List(2, 2)
    )

  it should "classify EVERY vertex, unlike the Delaney pipeline on an unclassifiable patch" in:
    val net = TilingBuilder.createTriangleNet(6, 6).value
    allAssert(
      net.patchVertexClasses().keySet shouldBe net.vertices.map(_.id).toSet,
      triangle.delaneyVertexClasses().isLeft shouldBe true,
      triangle.patchVertexClasses().keySet shouldBe triangle.vertices.map(_.id).toSet
    )

  it should "index orbits contiguously from zero" in:
    val classes = hexagonWithTriangle.patchVertexClasses()
    classes.values.toSet shouldBe (0 until classes.values.toSet.size).toSet

  it should "give every vertex its own orbit when the patch has no symmetry" in:
    val classes = asymmetricPatch.patchVertexClasses()
    allAssert(
      asymmetricPatch.patchSymmetryGroup().isTrivial shouldBe true,
      classes.values.toSet.size shouldBe asymmetricPatch.vertices.size
    )

  it should "be stable across repeated calls" in:
    hexagonWithTriangle.patchVertexClasses() shouldBe hexagonWithTriangle.patchVertexClasses()

  behavior of "patch orbits versus tiling orbits"

  /** The two surfaces answer different questions, and this is the shape of the difference: a finite net has
    * only the symmetry of its own outline, so it splits into far more orbits than the infinite tiling it
    * samples. Whenever the Delaney pipeline has an answer it is the one to prefer — these orbits are for the
    * patches where it has none.
    */
  it should "split a net into many more orbits than the tiling it samples" in:
    val net = TilingBuilder.createTriangleNet(6, 6).value
    allAssert(
      net.delaneyVertexClasses().value.values.toSet.size shouldBe 1,
      net.patchSymmetryGroup().name shouldBe "D2",
      net.patchVertexClasses().values.toSet.size shouldBe 16
    )

  /** A single polygon is the common editor case where the Delaney pipeline has no answer at all — no lattice
    * is detectable from one tile — and the patch group still gives the meaningful one.
    */
  it should "answer usefully on the patches the Delaney pipeline rejects" in:
    allAssert(
      hexagon.delaneyVertexClasses().isLeft shouldBe true,
      hexagon.patchVertexClasses().values.toSet shouldBe Set(0)
    )
