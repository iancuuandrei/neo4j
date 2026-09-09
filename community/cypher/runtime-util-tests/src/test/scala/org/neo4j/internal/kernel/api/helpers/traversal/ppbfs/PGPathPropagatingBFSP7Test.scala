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
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder
import org.neo4j.memory.MemoryTracker

import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.language.postfixOps

/**
 * P7 differential tests: post-saturation memoized completion must reproduce the exact result rows
 * and the exact propagation bookkeeping (schedules, target-signpost registrations) of exhaustive
 * tracing, while performing asymptotically less trace work on graphs whose compact signpost
 * representation holds combinatorially many paths (repeated diamonds: O(d) states, O(2^d) paths).
 */
class PGPathPropagatingBFSP7Test extends RuntimeUtilTestSuite with PGPathPropagatingBFSTestBase {

  private class CountingHooks extends PPBFSHooks {
    var pushes: Int = 0
    var schedules: Int = 0
    var targetSignposts: Int = 0
    var prunes: Int = 0
    var returned: Int = 0

    override def pushSignpost(stack: SignpostStack): Unit = pushes += 1

    override def schedule(
      nodeState: NodeState,
      lengthFromSource: Int,
      lengthToTarget: Int,
      source: GlobalState.ScheduleSource
    ): Unit = schedules += 1

    override def addTargetSignpost(signpost: TwoWaySignpost, lengthToTarget: Int): Unit =
      targetSignposts += 1

    override def pruneSourceLength(sourceSignpost: TwoWaySignpost, lengthFromSource: Int): Unit =
      prunes += 1

    override def returned(signposts: SignpostStack): Unit = returned += 1
  }

  /** Repeated diamonds: joins J0..Jd with two middle nodes per level, 2^d paths, O(d) signposts. */
  private def diamond(depth: Int): (InMemoryGraph, Long, Long) = {
    val g = InMemoryGraph.builder
    val joins = (0 to depth).map(_ => g.node()).toVector
    for (i <- 0 until depth) {
      val a = g.node()
      val b = g.node()
      g.rel(joins(i), a)
      g.rel(a, joins(i + 1))
      g.rel(joins(i), b)
      g.rel(b, joins(i + 1))
    }
    (g.build(), joins.head, joins.last)
  }

  private def anyDirectedWalk(sb: PGStateBuilder): Unit = {
    val s = sb.newState(isStartState = true, isFinalState = true)
    s.addRelationshipExpansion(s, direction = Direction.OUTGOING)
  }

  private def run(
    graph: InMemoryGraph,
    source: Long,
    intoTarget: Long,
    pathMode: TraversalPathMode,
    k: Int,
    p7Enabled: Boolean
  ): (Seq[Seq[Long]], CountingHooks) = {
    val hooks = new CountingHooks
    val fb = fixture()
      .withGraph(graph)
      .from(source)
      .withNfa(anyDirectedWalk)
      .withK(k)
      .withPathMode(pathMode)
      .copy(hooks = hooks)
    val fb2 = if (intoTarget == -1L) fb else fb.into(intoTarget)
    val rows = fb2
      .build((mt: MemoryTracker, h: PPBFSHooks) => {
        val tracer = new PathTracer[TracedPath](mt, fb2.tracker(mt, h), h)
        tracer.setP7Enabled(p7Enabled)
        tracer
      })
      .asScala
      .map(_.entities.map(_.id).toSeq)
      .toSeq
    (rows, hooks)
  }

  test("P7 preserves rows and propagation bookkeeping with less trace work on Walk diamonds") {
    val depth = 8
    val (graph, source, _) = diamond(depth)

    val (baseRows, base) = run(graph, source, -1L, TraversalPathMode.Walk, k = 1, p7Enabled = false)
    val (p7Rows, p7) = run(graph, source, -1L, TraversalPathMode.Walk, k = 1, p7Enabled = true)

    // identical result rows in identical order (all rows are yielded pre-saturation)
    p7Rows shouldBe baseRows
    // identical propagation bookkeeping: the internal state exhaustive tracing would produce
    p7.schedules shouldBe base.schedules
    p7.targetSignposts shouldBe base.targetSignposts
    p7.prunes shouldBe base.prunes
    p7.returned shouldBe base.returned
    // asymptotically less trace work: O(d) states instead of O(2^d) path combinations
    // (measured: depth 6 gives 948 baseline vs 200 P7 pushes; the gap widens exponentially)
    p7.pushes should be < base.pushes
    p7.pushes * 4 should be < base.pushes
    p7.pushes should be <= 60 * depth
  }

  test("P7 preserves rows on Walk diamonds with K=2") {
    val depth = 5
    val (graph, source, _) = diamond(depth)

    val (baseRows, base) = run(graph, source, -1L, TraversalPathMode.Walk, k = 2, p7Enabled = false)
    val (p7Rows, p7) = run(graph, source, -1L, TraversalPathMode.Walk, k = 2, p7Enabled = true)

    p7Rows shouldBe baseRows
    p7.schedules shouldBe base.schedules
    p7.targetSignposts shouldBe base.targetSignposts
    p7.pushes should be < base.pushes
  }

  test("P7 is inactive for bound targets: identical rows and trace work") {
    val depth = 4
    val (graph, source, target) = diamond(depth)

    val (baseRows, base) =
      run(graph, source, target, TraversalPathMode.Walk, k = 1, p7Enabled = false)
    val (p7Rows, p7) =
      run(graph, source, target, TraversalPathMode.Walk, k = 1, p7Enabled = true)

    p7Rows shouldBe baseRows
    p7.pushes shouldBe base.pushes
    p7.schedules shouldBe base.schedules
  }

  test("P7 is inactive for Trail: identical rows and bookkeeping") {
    val depth = 4
    val (graph, source, _) = diamond(depth)

    val (baseRows, base) = run(graph, source, -1L, TraversalPathMode.Trail, k = 1, p7Enabled = false)
    val (p7Rows, p7) = run(graph, source, -1L, TraversalPathMode.Trail, k = 1, p7Enabled = true)

    p7Rows shouldBe baseRows
    p7.pushes shouldBe base.pushes
    p7.schedules shouldBe base.schedules
    p7.targetSignposts shouldBe base.targetSignposts
  }
}
