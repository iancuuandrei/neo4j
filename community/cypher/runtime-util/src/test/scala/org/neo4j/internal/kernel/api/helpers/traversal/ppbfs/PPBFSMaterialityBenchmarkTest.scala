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
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.FoundNodes.LookupLocation
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import scala.collection.mutable.ArrayBuffer

/**
 * Experimental causal harness. This class belongs only to the research commits and must not be retained in the final
 * production patch.
 */
class PPBFSMaterialityBenchmarkTest extends RuntimeUtilTestSuite with PGPathPropagatingBFSTestBase {

  final private class Metrics extends PPBFSHooks {
    var lookups = 0L
    var historyProbes = 0L
    var historyHits = 0L
    var misses = 0L
    var maxHistoryDepth = 0
    var maxHistoryProbes = 0
    var nodeLevelBuckets = 0L
    var allocatedSlots = 0L

    override def foundNodesLookup(
      location: LookupLocation,
      probes: Int,
      historyHitAge: Int,
      historyDepth: Int
    ): Unit = {
      lookups += 1
      historyProbes += probes
      maxHistoryDepth = math.max(maxHistoryDepth, historyDepth)
      maxHistoryProbes = math.max(maxHistoryProbes, probes)
      if (location == LookupLocation.HISTORY) historyHits += 1
      if (location == LookupLocation.MISS) misses += 1
    }

    override def foundNodesBufferAdd(newNodeBucket: Boolean, slots: Int): Unit =
      if (newNodeBucket) {
        nodeLevelBuckets += 1
        allocatedSlots += slots
      }
  }

  private def runChain(depth: Int): (Metrics, Long, Int) = {
    val graph = InMemoryGraph.builder
    val nodes = Array.fill(depth + 1)(graph.node())
    for (i <- 0 until depth) graph.rel(nodes(i), nodes(i + 1))

    val metrics = new Metrics
    val start = System.nanoTime()
    val paths = fixture()
      .withGraph(graph.build())
      .from(nodes.head)
      .into(nodes.last)
      .withK(1)
      .withPathMode(TraversalPathMode.Walk)
      .withNfa { sb =>
        val start = sb.newState("start", isStartState = true)
        val loop = sb.newState("loop", isFinalState = true)
        start.addRelationshipExpansion(loop, direction = Direction.OUTGOING)
        loop.addRelationshipExpansion(loop, direction = Direction.OUTGOING)
      }
      .withHooks(metrics)
      .paths()
    val elapsed = System.nanoTime() - start

    paths should have size 1
    paths.head.length shouldBe depth * 2 + 1
    (metrics, elapsed, paths.head.length)
  }

  test("record controlled chain history-probe scaling") {
    val depths = sys.props
      .get("ppbfs.depths")
      .map(_.split(',').map(_.trim.toInt).toSeq)
      .getOrElse(Seq(4, 16, 64, 256, 1024, 4096))
    val rows = ArrayBuffer(
      "depth,lookups,history_probes,probes_per_lookup,history_hits,misses,max_history_depth,max_history_probes," +
        "node_level_buckets,allocated_state_slots,elapsed_ns,path_entities"
    )

    depths.foreach { depth =>
      val (metrics, elapsed, pathEntities) = runChain(depth)
      rows += Seq(
        depth,
        metrics.lookups,
        metrics.historyProbes,
        metrics.historyProbes.toDouble / metrics.lookups,
        metrics.historyHits,
        metrics.misses,
        metrics.maxHistoryDepth,
        metrics.maxHistoryProbes,
        metrics.nodeLevelBuckets,
        metrics.allocatedSlots,
        elapsed,
        pathEntities
      ).mkString(",")
    }

    val output = Path.of(sys.props("ppbfs.metrics.output"))
    Files.createDirectories(output.getParent)
    Files.writeString(output, rows.mkString("\n") + "\n", StandardCharsets.UTF_8)

    val first = rows(1).split(',')
    val last = rows.last.split(',')
    last(2).toLong should be > first(2).toLong
    last(3).toDouble should be > first(3).toDouble
  }
}
