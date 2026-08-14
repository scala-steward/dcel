package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.TilingDelaney.*
import io.github.scala_tessella.dcel.conversion.TilingSVG
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
    holedNet((i, j) => (i + 3 * j) % 13 == 0).exactUniformity.value shouldBe 6

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
