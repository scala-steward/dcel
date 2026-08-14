package io.github.scala_tessella.dcel

import io.github.scala_tessella.dcel.TilingPatchSymmetry.*
import io.github.scala_tessella.dcel.conversion.TilingSVG
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The fixture-loading half of [[TilingPatchSymmetrySpec]]. JVM-only because `loadFile` reads from disk,
  * which does not link under Scala.js — the same reason [[TilingDelaneySpec]] lives here.
  */
class TilingPatchSymmetryFixtureSpec extends AnyFlatSpec with Matchers with TilingTestHelpers:

  behavior of "TilingPatchSymmetry on the 3.6.3.6 fixture"

  /** The trihexagonal patch the heuristic `uniformityTree` splits into 2 classes and the Delaney pipeline
    * correctly calls 1-uniform (ADR-0019). As a drawn shape it is a hexagonal rosette, so the patch group
    * sees the full `D6` of its outline.
    */
  it should "see the hexagonal outline of the patch" in:
    val tiling = TilingSVG.fromMetadata(loadFile("metadata/3.6.3.6_uniformity_issue.xml")).value
    allAssert(
      tiling.patchSymmetryGroup().name shouldBe "D6",
      tiling.patchVertexClasses().keySet shouldBe tiling.vertices.map(_.id).toSet,
      tiling.patchVertexClasses().values.toSet.size shouldBe 14
    )
