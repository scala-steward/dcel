package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.TilingDelaney.*
import io.github.scala_tessella.dcel.conversion.TilingSVG
import io.github.scala_tessella.dcel.geometry.RegularPolygon
import io.github.scala_tessella.dcel.structure.VertexId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.Ordering.Implicits.*

class TilingDelaneySpec extends AnyFlatSpec with Matchers with TilingTestHelpers:

  behavior of "TilingDelaney.delaneyClassification()"

  it should "classify the square net as the 1-uniform 4.4.4.4 tiling" in:
    val result = TilingBuilder.createRhombusNet(6, 6).value.delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 1,
      result.vertexConfigs shouldBe List(List(4, 4, 4, 4))
    )

  it should "classify the triangle net as the 1-uniform 3.3.3.3.3.3 tiling" in:
    val result = TilingBuilder.createTriangleNet(8, 8).value.delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 1,
      result.vertexConfigs shouldBe List(List(3, 3, 3, 3, 3, 3))
    )

  it should "classify the hexagon net as the 1-uniform 6.6.6 tiling" in:
    val result = TilingBuilder.createHexagonNet(5, 5).value.delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 1,
      result.vertexConfigs shouldBe List(List(6, 6, 6))
    )

  it should "fail on a single polygon (no lattice)" in:
    square.delaneyClassification().left.value.message should include("No translation lattice")

  behavior of "the 3.6.3.6 problematic tiling"

  /** The fixture behind the disabled `uniformityTree` regression (`TilingUniformitySpec`): a trihexagonal
    * patch the boundary-signature refinement split into 2 classes. The correct uniformity is 1. <img
    * src="file:../../../../../resources/uniformityIssue.svg"/>
    */
  it should "have exact uniformity 1" in:
    val xmlMetadata = loadFile("metadata/3.6.3.6_uniformity_issue.xml")
    val tiling      = TilingSVG.fromMetadata(xmlMetadata).value
    val result      = tiling.delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 2,
      result.vertexConfigs shouldBe List(List(3, 6, 3, 6))
    )

  behavior of "the holed triangle net uniformity fixtures"

  private def holedNet(predicate: (Int, Int) => Boolean): Tiling =
    TilingBuilder.createHoledTriangleNet(9, 9)(predicate).value

  it should "classify the 1-uniform fixture as the snub trihexagonal tiling 3.3.3.3.6" in:
    val result = holedNet((i, j) => (i + 3 * j) % 7 == 0).delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 1,
      // the snub's triangles fall into two transitivity classes alongside the hexagon
      result.gonality shouldBe 3,
      result.vertexConfigs shouldBe List(List(3, 3, 3, 3, 6))
    )

  it should "confirm the 2-uniform fixture" in:
    val result = holedNet((i, j) => i % 3 == 0 && j % 3 == 0).delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 2,
      result.vertexConfigs.sorted shouldBe List(List(3, 3, 3, 3, 3, 3), List(3, 3, 3, 3, 6))
    )

  it should "confirm the 3-uniform fixture" in:
    val result = holedNet((i, j) => (i + 4 * j) % 7 == 0).delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 3,
      result.vertexConfigs.sorted shouldBe
        List(List(3, 3, 3, 3, 3, 3), List(3, 3, 3, 3, 6), List(3, 3, 6, 6))
    )

  it should "confirm the 4-uniform fixture" in:
    val result = holedNet((i, j) => (i + 7 * j) % 9 == 0).delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 4,
      // 4 orbits over 2 distinct types: three 3.3.3.3.6 orbits and one 3.3.3.3.3.3
      result.vertexConfigs.sorted shouldBe
        List(List(3, 3, 3, 3, 3, 3), List(3, 3, 3, 3, 6), List(3, 3, 3, 3, 6), List(3, 3, 3, 3, 6))
    )

  it should "confirm the 5-uniform fixture (strict period validation finds the genuine lattice)" in:
    val result = holedNet((i, j) => i % 10 == (j * 8) % 10).delaneyClassification().value
    allAssert(
      result.uniformity shouldBe 5,
      result.vertexConfigs.sorted shouldBe
        List(
          List(3, 3, 3, 3, 3, 3),
          List(3, 3, 3, 3, 3, 3),
          List(3, 3, 3, 3, 3, 3),
          List(3, 3, 3, 3, 6),
          List(3, 3, 6, 6)
        )
    )

  it should "confirm the 6-uniform fixture" in:
    holedNet((i, j) => (i + 3 * j) % 13 == 0).exactUniformity().value shouldBe 6

  behavior of "TilingDelaney vertex and face classes"

  it should "assign every vertex of the square net to the single class" in:
    val net     = TilingBuilder.createRhombusNet(6, 6).value
    val classes = net.delaneyVertexClasses().value
    allAssert(
      classes.keySet shouldBe net.vertices.map(_.id).toSet,
      classes.values.toSet shouldBe Set(0)
    )

  it should "assign the 3.6.3.6 fixture's vertices to one class and its faces to two" in:
    val tiling        = TilingSVG.fromMetadata(loadFile("metadata/3.6.3.6_uniformity_issue.xml")).value
    val vertexClasses = tiling.delaneyVertexClasses().value
    val faceClasses   = tiling.delaneyFaceClasses().value
    val sizeOf        = tiling.innerFaces.map(f => f.id -> f.getVerticesUnsafe.size).toMap
    allAssert(
      vertexClasses.keySet shouldBe tiling.vertices.map(_.id).toSet,
      vertexClasses.values.toSet shouldBe Set(0),
      faceClasses.keySet shouldBe tiling.innerFaces.map(_.id).toSet,
      faceClasses.values.toSet shouldBe Set(0, 1),
      // the two face classes are exactly the triangles and the hexagons
      faceClasses.groupBy(_._2).values.map(_.keySet.map(sizeOf).toList.distinct).toSet shouldBe
        Set(List(3), List(6))
    )

  it should "split the 2-uniform fixture's vertices into two classes" in:
    val net     = holedNet((i, j) => i % 3 == 0 && j % 3 == 0)
    val classes = net.delaneyVertexClasses().value
    allAssert(
      classes.keySet shouldBe net.vertices.map(_.id).toSet,
      classes.values.toSet shouldBe Set(0, 1)
    )

  behavior of "the 3.3.6.6.i fixture (old claim: uniformity 5)"

  /** The `TilingUniformitySpec` construction: a hexagon net (all triangles of the holed net die) whose
    * boundary notches are decorated with 6 rhombus wedges. Investigation (2026-08-14) showed the decoration
    * is PARTIAL: the rhombi mark hexagons of the decoration lattice for subdivision into 6 unit triangles
    * (the only way to complete a rhombus wedge inside a hexagonal gap), but only 6 of the ~10 lattice cells
    * inside the patch are decorated, each with only 2 of its 6 forced triangles. The patch is therefore not a
    * periodic sample of any tiling, and the pipeline says so.
    */
  private def partiallyDecorated: Tiling =
    TilingBuilder.createHoledTriangleNet(9, 11)((i, j) => (i - j) % 3 == 0)
      .value
      .maybeAddRegularPolygon(VertexId(24), VertexId(25), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(25), VertexId(35), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(27), VertexId(28), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(28), VertexId(38), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(54), VertexId(55), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(55), VertexId(65), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(57), VertexId(58), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(58), VertexId(68), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(84), VertexId(85), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(85), VertexId(95), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(87), VertexId(88), RegularPolygon(3)).value
      .maybeAddRegularPolygon(VertexId(88), VertexId(98), RegularPolygon(3)).value

  it should "fail honestly: the partially decorated patch is not a periodic sample" in:
    val error = partiallyDecorated.delaneyClassification().left.value
    error shouldBe a[PeriodicityError]
    error.message should include("360")

  it should "classify the completed decoration as the 2-uniform tiling [3.3.3.3.3.3; 3.3.6.6]" in:
    // the tiling the rhombus hints force: every decoration-lattice hexagon starred into 6 triangles,
    // built directly by keeping the starred centres of the holed net
    val result = TilingBuilder
      .createHoledTriangleNet(12, 12)((i, j) => (i - j) % 3 == 0 && !(i % 3 == 0 && j % 3 == 0))
      .value
      .delaneyClassification()
      .value
    allAssert(
      result.uniformity shouldBe 2,
      result.gonality shouldBe 2,
      result.vertexConfigs.sorted shouldBe List(List(3, 3, 3, 3, 3, 3), List(3, 3, 6, 6))
    )

  behavior of "canonical keys"

  it should "identify mirror-image patches of the chiral snub trihexagonal tiling" in:
    val key       = holedNet((i, j) => (i + 3 * j) % 7 == 0).delaneyClassification().value.canonicalKey
    val mirrorKey = holedNet((i, j) => (j + 3 * i) % 7 == 0).delaneyClassification().value.canonicalKey
    // Delaney–Dress symbols include reflections: enantiomorphs share the same minimal symbol
    mirrorKey shouldBe key

  it should "give the same key to different patches of the same tiling" in:
    val key5x5 = TilingBuilder.createRhombusNet(5, 5).value.delaneyClassification().value.canonicalKey
    val key7x6 = TilingBuilder.createRhombusNet(7, 6).value.delaneyClassification().value.canonicalKey
    key7x6 shouldBe key5x5

  it should "give different keys to different tilings" in:
    val squareKey   = TilingBuilder.createRhombusNet(5, 5).value.delaneyClassification().value.canonicalKey
    val triangleKey = TilingBuilder.createTriangleNet(8, 8).value.delaneyClassification().value.canonicalKey
    squareKey should not be triangleKey
