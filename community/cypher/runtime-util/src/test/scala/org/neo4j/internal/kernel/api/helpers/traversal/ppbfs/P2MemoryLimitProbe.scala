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

import org.neo4j.cypher.internal.runtime.RuntimeUtilTestSuite
import org.neo4j.cypher.internal.util.test_helpers.InMemoryGraph
import org.neo4j.graphdb.Direction
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.NfaDsl.Implicits._
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.PGPathPropagatingBFSTestBase.Nfa
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.PGPathPropagatingBFSTestBase.nfa
import org.neo4j.memory.LocalMemoryTracker
import org.neo4j.memory.MemoryLimitExceededException
import org.neo4j.memory.MemoryPoolImpl
import org.neo4j.memory.MemoryTracker

import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * RESEARCH-ONLY P2 memory-limit probe. Must never enter a production or contribution branch.
 *
 * Binary-searches the lowest passing LocalMemoryTracker limit for fixed workloads. A lower
 * passing limit means the query survives tighter configured query-memory budgets; this is a
 * configured-limit claim, NOT a resident-memory claim. Same file runs on B0 and candidate
 * worktrees (`-Dp2.variant=` labels the output row).
 */
class P2MemoryLimitProbe extends RuntimeUtilTestSuite with PGPathPropagatingBFSTestBase {

  private def repChainNfa(reps: Int): Nfa =
    nfa(s"rep-chain-$reps") { sb =>
      val s = sb.newState("s", isStartState = true)
      var prev = s
      for (_ <- 0 until reps) {
        val a = sb.newState("a")
        val bSt = sb.newState("b")
        prev.addNodeJuxtaposition(a)
        a.addRelationshipExpansion(bSt, direction = Direction.OUTGOING)
        prev = bSt
      }
      val anon = sb.newState("anon")
      val t = sb.newState("t", isFinalState = true)
      prev.addNodeJuxtaposition(anon)
      anon.addNodeJuxtaposition(t)
      s.addNodeJuxtaposition(anon)
    }

  private def tryRun(graph: InMemoryGraph, source: Long, nfa: Nfa, limit: Long): Boolean = {
    val pool = new MemoryPoolImpl(0, true, null)
    val mt: MemoryTracker = new LocalMemoryTracker(pool, limit, 1024, null)
    try {
      val iter = fixture()
        .withGraph(graph)
        .from(source)
        .withNfa(nfa)
        .withMemoryTracker(mt)
        .build()
        .asScala
      var rows = 0L
      while (iter.hasNext) { iter.next(); rows += 1 }
      true
    } catch {
      // Only the memory budget may fail a probe run; any other exception is a real defect.
      case _: MemoryLimitExceededException => false
    }
  }

  private def lowestPassing(id: String, graph: InMemoryGraph, source: Long, nfa: Nfa): Long = {
    // exponential search for an upper bound, then binary search (limit=0 means NO_LIMIT: always passes)
    var lo = 0L
    var hi = 1024L
    while (!tryRun(graph, source, nfa, hi) && hi < (1L << 40)) hi *= 2
    assert(tryRun(graph, source, nfa, hi), s"$id does not pass even at $hi")
    while (hi - lo > 1024) {
      val mid = lo + (hi - lo) / 2
      if (tryRun(graph, source, nfa, mid)) hi = mid else lo = mid
    }
    hi
  }

  test("p2 memory limits") {
    val variant = Option(System.getProperty("p2.variant")).getOrElse("unknown")
    val b = InMemoryGraph.builder
    val chain = b.line(1999)
    val chainGraph = b.build()
    val b2 = InMemoryGraph.builder
    val center = b2.node()
    (0 until 2000).foreach(_ => b2.rel(center, b2.node()))
    val starGraph = b2.build()

    val chainLimit = lowestPassing("chain2000-s255", chainGraph, chain.head, repChainNfa(126))
    println(s"[$variant/chain2000-s255] lowestPassingBytes=$chainLimit")
    val starLimit = lowestPassing("star2000-s33", starGraph, center, {
      nfa("fanout") { sb =>
        val s = sb.newState("s", isStartState = true)
        for (i <- 0 until 32) {
          val f = sb.newState(if (i == 0) "f" else "g", isFinalState = i == 0)
          s.addRelationshipExpansion(f, types = Array(i + 1), direction = Direction.OUTGOING)
        }
      }
    })
    println(s"[$variant/star2000-s33] lowestPassingBytes=$starLimit")
  }
}
