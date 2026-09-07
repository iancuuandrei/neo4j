/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs

import org.neo4j.cypher.internal.logical.plans.TraversalPathMode
import org.neo4j.cypher.internal.runtime.RuntimeUtilTestSuite
import org.neo4j.cypher.internal.util.test_helpers.InMemoryGraph
import org.neo4j.graphdb.Direction
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
 * Research-only deterministic PPBFS workload generator for P2 state-bucket occupancy.
 *
 * The generated graph is a shallow fan-out of disjoint equal-length chains. The NFA has one start state, k active
 * lane states which all match every relationship, and S-k-1 unreachable padding states. Every non-source graph node
 * reached before target saturation should therefore have k active product states. Padding is deliberate: this direct
 * harness isolates the state-bucket mechanism. Planner-generated reachable-NFA workloads are a separate validation
 * layer and must not be replaced by this test.
 */
class P2StateBucketWorkloadTest extends RuntimeUtilTestSuite with PGPathPropagatingBFSTestBase {

  test("record controlled P2 state-bucket occupancy matrix") {
    assume(
      sys.props.contains(P2StateBucketTelemetry.OUTPUT_PROPERTY),
      s"set -D${P2StateBucketTelemetry.OUTPUT_PROPERTY}=<absolute-jsonl-path> to request the experiment"
    )
    assume(
      sys.props.contains("ppbfs.p2.synthetic.manifest.output"),
      "set -Dppbfs.p2.synthetic.manifest.output=<absolute-csv-path> to request the experiment"
    )

    val manifestOutput = Path.of(sys.props("ppbfs.p2.synthetic.manifest.output"))
    val stateCounts = ints("ppbfs.p2.synthetic.state-counts", Seq(4, 8, 16, 32, 64, 128, 256))
    val requestedOccupancies = ints("ppbfs.p2.synthetic.occupancies", Seq(1, 2, 3, 4, 6, 8, 16, 32, 64, 96, 127))
    val shapes = sys.props
      .get("ppbfs.p2.synthetic.shapes")
      .map(_.split(',').iterator.map(value => IdShape.parse(value.trim)).toSeq)
      .getOrElse(Seq(IdShape.Low, IdShape.High, IdShape.Spread, IdShape.Randomized))
    val depth = sys.props.get("ppbfs.p2.synthetic.depth").fold(8)(_.toInt)
    val width = sys.props.get("ppbfs.p2.synthetic.width").fold(64)(_.toInt)
    val seed = sys.props.get("ppbfs.p2.synthetic.seed").fold(0x5eedL)(_.toLong)

    require(stateCounts.forall(_ >= 2), "state counts must be at least two")
    require(requestedOccupancies.forall(_ >= 1), "occupancies must be positive")
    require(depth >= 2, "depth must be at least two so committed buckets are also expanded")
    require(width >= 1, "width must be positive")

    val (graph, source, target) = fanoutChains(depth, width)
    val rows = ArrayBuffer(
      "workload_id,S,target_k,id_shape,depth,width,expected_role,active_state_ids,result_count," +
        "path_entities,result_sha256"
    )
    val previousRunId = sys.props.get(P2StateBucketTelemetry.RUN_ID_PROPERTY)

    try {
      for {
        stateCount <- stateCounts
        targetK <- requestedOccupancies.distinct.sorted if targetK < stateCount
        shape <- shapes
      } {
        val activeStateIds = shape.ids(stateCount, targetK, seed)
        activeStateIds should have size targetK
        activeStateIds.distinct should have size targetK
        activeStateIds.foreach { stateId =>
          stateId should be > 0
          stateId should be < stateCount
        }

        val expectedRole = classify(stateCount, targetK)
        val workloadId = s"synthetic-S$stateCount-k$targetK-${shape.name}-d$depth-w$width"
        System.setProperty(P2StateBucketTelemetry.RUN_ID_PROPERTY, workloadId)

        val paths = fixture()
          .withGraph(graph)
          .from(source)
          .into(target, SearchMode.Unidirectional)
          .withK(1)
          .withPathMode(TraversalPathMode.Walk)
          .withNfa(builder => configureNfa(builder, stateCount, activeStateIds))
          .paths()

        paths should have size 1
        paths.head should have size depth * 2 + 1
        rows += Seq(
          workloadId,
          stateCount,
          targetK,
          shape.name,
          depth,
          width,
          expectedRole,
          activeStateIds.mkString(";"),
          paths.size,
          paths.head.size,
          sha256(paths.head.mkString(","))
        ).mkString(",")
      }
    } finally {
      previousRunId match {
        case Some(value) => System.setProperty(P2StateBucketTelemetry.RUN_ID_PROPERTY, value)
        case None        => System.clearProperty(P2StateBucketTelemetry.RUN_ID_PROPERTY)
      }
    }

    Option(manifestOutput.getParent).foreach(parent => Files.createDirectories(parent))
    Files.writeString(
      manifestOutput,
      rows.mkString("\n") + "\n",
      StandardCharsets.UTF_8
    )
  }

  private def configureNfa(builder: PGStateBuilder, stateCount: Int, activeStateIds: Seq[Int]): Unit = {
    val finalStateId = activeStateIds.head
    val states = Array.tabulate(stateCount) { stateId =>
      builder.newState(
        s"q$stateId",
        isStartState = stateId == 0,
        isFinalState = stateId == finalStateId
      )
    }

    activeStateIds.foreach { stateId =>
      states(0).addRelationshipExpansion(states(stateId), direction = Direction.OUTGOING)
      states(stateId).addRelationshipExpansion(states(stateId), direction = Direction.OUTGOING)
    }
  }

  private def fanoutChains(depth: Int, width: Int): (InMemoryGraph, Long, Long) = {
    val builder = InMemoryGraph.builder
    val source = builder.node()
    var target = -1L

    for (lane <- 0 until width) {
      var previous = source
      for (_ <- 0 until depth) {
        val next = builder.node()
        builder.rel(previous, next)
        previous = next
      }
      if (lane == 0) {
        target = previous
      }
    }

    (builder.build(), source, target)
  }

  private def ints(property: String, default: Seq[Int]): Seq[Int] =
    sys.props
      .get(property)
      .map(_.split(',').iterator.map(_.trim).filter(_.nonEmpty).map(_.toInt).toSeq)
      .getOrElse(default)

  private def classify(stateCount: Int, active: Int): String = {
    val occupancy = active.toDouble / stateCount
    if (stateCount <= 8 || occupancy >= 0.75) "negative"
    else if (stateCount >= 32 && active <= 4) "positive"
    else "crossover"
  }

  private def sha256(value: String): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString

  private sealed trait IdShape {
    def name: String
    def ids(stateCount: Int, active: Int, seed: Long): Seq[Int]
  }

  private object IdShape {
    case object Low extends IdShape {
      override val name: String = "low"
      override def ids(stateCount: Int, active: Int, seed: Long): Seq[Int] =
        1 to active
    }

    case object High extends IdShape {
      override val name: String = "high"
      override def ids(stateCount: Int, active: Int, seed: Long): Seq[Int] =
        (stateCount - active until stateCount).toSeq
    }

    case object Spread extends IdShape {
      override val name: String = "spread"
      override def ids(stateCount: Int, active: Int, seed: Long): Seq[Int] =
        if (active == 1) {
          Seq(math.max(1, stateCount / 2))
        } else {
          (0 until active).map { index =>
            1 + ((index.toLong * (stateCount - 2)) / (active - 1)).toInt
          }
        }
    }

    case object Randomized extends IdShape {
      override val name: String = "random"
      override def ids(stateCount: Int, active: Int, seed: Long): Seq[Int] =
        new Random(seed ^ (stateCount.toLong << 32) ^ active.toLong)
          .shuffle(1 until stateCount)
          .take(active)
          .sorted
    }

    def parse(value: String): IdShape =
      value.toLowerCase match {
        case Low.name        => Low
        case High.name       => High
        case Spread.name     => Spread
        case Randomized.name => Randomized
        case other           => throw new IllegalArgumentException(s"unknown P2 state-ID shape: $other")
      }
  }
}
