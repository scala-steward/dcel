package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.TilingDelaney.*
import io.github.scala_tessella.dcel.conversion.TilingSVG
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TilingDelaneySpec extends AnyFlatSpec with Matchers with TilingTestHelpers:

  behavior of "TilingDelaney.delaneyClassification"

  it should "classify the square net as the 1-uniform 4.4.4.4 tiling" in:
    val result = TilingBuilder.createRhombusNet(6, 6).value.delaneyClassification.value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 1,
      result.vertexConfigs shouldBe List(List(4, 4, 4, 4))
    )

  it should "classify the triangle net as the 1-uniform 3.3.3.3.3.3 tiling" in:
    val result = TilingBuilder.createTriangleNet(8, 8).value.delaneyClassification.value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 1,
      result.vertexConfigs shouldBe List(List(3, 3, 3, 3, 3, 3))
    )

  it should "classify the hexagon net as the 1-uniform 6.6.6 tiling" in:
    val result = TilingBuilder.createHexagonNet(5, 5).value.delaneyClassification.value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 1,
      result.vertexConfigs shouldBe List(List(6, 6, 6))
    )

  it should "fail on a single polygon (no lattice)" in:
    square.delaneyClassification.left.value should include("No translation lattice")

  behavior of "the 3.6.3.6 problematic tiling"

  /** The fixture behind the disabled `uniformityTree` regression (`TilingUniformitySpec`): a trihexagonal
    * patch the boundary-signature refinement split into 2 classes. The correct uniformity is 1. <img
    * src="file:../../../../../resources/uniformityIssue.svg"/>
    */
  it should "have exact uniformity 1" in:
    val xmlMetadata = loadFile("metadata/3.6.3.6_uniformity_issue.xml")
    val tiling      = TilingSVG.fromMetadata(xmlMetadata).value
    val result      = tiling.delaneyClassification.value
    allAssert(
      result.uniformity shouldBe 1,
      result.gonality shouldBe 2,
      result.vertexConfigs shouldBe List(List(3, 6, 3, 6))
    )
