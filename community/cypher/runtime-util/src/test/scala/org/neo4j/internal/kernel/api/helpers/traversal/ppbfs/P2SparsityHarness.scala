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
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.NfaDsl.Implicits._
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.PGPathPropagatingBFSTestBase.Nfa
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.PGPathPropagatingBFSTestBase.nfa
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * RESEARCH-ONLY P2 sparsity harness. Must never enter a production or contribution branch.
 *
 * Runs controlled synthetic PPBFS workloads on the dense baseline with per-bucket telemetry enabled
 * and records a canonical result oracle (row count, total length, multiset hash) per workload for
 * later candidate equivalence checks. Workload intents below are preregistered hypotheses; final
 * roles are frozen only after baseline telemetry (see research/ppbfs/p2/SPARSITY_REPORT.md).
 *
 * Single-threaded by construction (ScalaTest runs suite tests sequentially; telemetry holds one run).
 */
class P2SparsityHarness extends RuntimeUtilTestSuite with PGPathPropagatingBFSTestBase {

  private val artifactRoot: Path =
    Path.of(Option(System.getenv("PPBFS_ARTIFACTS")).getOrElse("D:/dev/neo4j-research/artifacts/ppbfs"))

  private def ensureTelemetry(): Path = {
    val dir = artifactRoot.resolve("runs/p2-telemetry-v1")
    Files.createDirectories(dir)
    if (System.getProperty(P2Telemetry.DIRECTORY_PROPERTY) == null) {
      System.setProperty(P2Telemetry.DIRECTORY_PROPERTY, dir.toString)
    }
    dir
  }

  private case class Oracle(rowCount: Long, lengthSum: Long, hash: String)

  private var pendingOracle: Oracle = _

  private def run(id: String,
    graph: InMemoryGraph,
    source: Long,
    nfa: Nfa,
    searchMode: SearchMode = SearchMode.Unidirectional,
    intoTarget: Long = -1L,
    maxDepth: Int = -1,
    k: Int = Int.MaxValue,
    pathMode: TraversalPathMode = TraversalPathMode.Trail,
    crossCheck: Boolean = false): Unit = {
    ensureTelemetry()
    val probe = new PGStateBuilder
    nfa(probe)
    val s = probe.stateCount
    P2Telemetry.beginRun(id, s, searchMode)
    val fb0 = fixture()
      .withGraph(graph)
      .from(source)
      .withNfa(nfa)
      .withMode(searchMode)
      .withPathMode(pathMode)
      .withMaxDepth(maxDepth)
      .withK(k)
    val fb = if (intoTarget != -1L) fb0.into(intoTarget, searchMode) else fb0
    val iter = fb.build().asScala
    val perPath = new java.util.ArrayList[String]()
    var rows = 0L
    var lenSum = 0L
    while (iter.hasNext) {
      val path = iter.next()
      rows += 1
      val ids = path.entities.map(_.id)
      lenSum += ids.size
      val md = MessageDigest.getInstance("SHA-256")
      ids.foreach(l => md.update(java.nio.ByteBuffer.allocate(8).putLong(l).array()))
      perPath.add(md.digest().map("%02x".format(_)).mkString)
    }
    import scala.jdk.CollectionConverters._
    val finalMd = MessageDigest.getInstance("SHA-256")
    perPath.asScala.sorted.foreach(h => finalMd.update(h.getBytes(StandardCharsets.UTF_8)))
    pendingOracle = Oracle(rows, lenSum, finalMd.digest().map("%02x".format(_)).mkString)
    if (crossCheck) fb.assertExpected()
    val dir = P2Telemetry.endRun()
    val oracleLine = s"rows=$rows lenSum=$lenSum hash=${pendingOracle.hash}"
    println(s"[$id] S=$s $oracleLine dir=$dir")
    Files.writeString(
      dir.resolve("oracle.txt"),
      s"workload=$id\nS=$s\nrows=$rows\nlengthSum=$lenSum\nhash=${pendingOracle.hash}\n",
      StandardCharsets.UTF_8)
  }

  // ---------- graph builders ----------

  private def chainGraph(n: Int): (InMemoryGraph, Long) = {
    val b = InMemoryGraph.builder
    val nodes = b.line(n - 1)
    (b.build(), nodes.head)
  }

  private def gridGraph(w: Int, h: Int): (InMemoryGraph, Long) = {
    val b = InMemoryGraph.builder
    val ids = Array.tabulate(w * h)(_ => b.node())
    for (y <- 0 until h; x <- 0 until w) {
      if (x + 1 < w) { b.rel(ids(y * w + x), ids(y * w + x + 1)); b.rel(ids(y * w + x + 1), ids(y * w + x)) }
      if (y + 1 < h) { b.rel(ids(y * w + x), ids((y + 1) * w + x)); b.rel(ids((y + 1) * w + x), ids(y * w + x)) }
    }
    (b.build(), ids(0))
  }

  private def starGraph(leaves: Int): (InMemoryGraph, Long) = {
    val b = InMemoryGraph.builder
    val center = b.node()
    (0 until leaves).foreach(_ => b.rel(center, b.node()))
    (b.build(), center)
  }

  /** Layered reconvergent graph: source -> L0 (width w) -> L1 (width w) -> ... output node. */
  private def diamondGraph(layers: Int, width: Int): (InMemoryGraph, Long) = {
    val b = InMemoryGraph.builder
    val source = b.node()
    var prev: Seq[Long] = Seq(source)
    for (_ <- 0 until layers) {
      val cur = (0 until width).map(_ => b.node())
      for (a <- prev; c <- cur) b.rel(a, c)
      prev = cur
    }
    val out = b.node()
    prev.foreach(a => b.rel(a, out))
    (b.build(), source)
  }

  /**
   * Layered fanout graph for large-k falsification: source -> L0 (w nodes, complete) ->
   * L1 (f nodes, complete bipartite). An L0 node reached in B branch states has k=B actives.
   */
  private def fanoutLayerGraph(w: Int, f: Int): (InMemoryGraph, Long) = {
    val b = InMemoryGraph.builder
    val source = b.node()
    val l0 = (0 until w).map(_ => b.node())
    val l1 = (0 until f).map(_ => b.node())
    l0.foreach(m => b.rel(source, m))
    for (m <- l0; n <- l1) b.rel(m, n)
    (b.build(), source)
  }

  // ---------- NFA builders ----------

  /** Linear chain NFA with 2*reps+3 states: s, (a,b)*reps unrolled, anon, t. */
  private def repChainNfa(reps: Int): Nfa =
    nfa(s"rep-chain-$reps") { sb =>
      import org.neo4j.graphdb.Direction
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

  /**
   * Parallel-branch NFA: s -e-> a_i -e-> f for B matching branches plus U non-matching
   * branches on rel type 2 (absent from test graphs). Measured S = 2+B+U (one intermediate
   * state per branch plus shared start/final); depth-1 buckets hold k = B.
   */
  private def branchNfa(matching: Int, nonMatching: Int): Nfa =
    nfa(s"branch-B${matching}U$nonMatching") { sb =>
      import org.neo4j.graphdb.Direction
      val s = sb.newState("s", isStartState = true)
      val f = sb.newState("f", isFinalState = true)
      for (_ <- 0 until matching) {
        val a = sb.newState("a")
        s.addRelationshipExpansion(a, types = Array(1), direction = Direction.OUTGOING)
        a.addRelationshipExpansion(f, types = Array(1), direction = Direction.OUTGOING)
      }
      for (_ <- 0 until nonMatching) {
        val a = sb.newState("u")
        s.addRelationshipExpansion(a, types = Array(2), direction = Direction.OUTGOING)
        a.addRelationshipExpansion(f, types = Array(2), direction = Direction.OUTGOING)
      }
    }

  /** Single-expansion NFA with B rel-type-specific branches, only type 1 present in the graph. */
  private def fanoutNfa(branches: Int): Nfa =
    nfa(s"fanout-$branches") { sb =>
      import org.neo4j.graphdb.Direction
      val s = sb.newState("s", isStartState = true)
      for (i <- 0 until branches) {
        val f = sb.newState(if (i == 0) "f" else "g", isFinalState = i == 0)
        s.addRelationshipExpansion(f, types = Array(i + 1), direction = Direction.OUTGOING)
      }
    }

  // ---------- workloads ----------

  test("p2 chain S31 k~1 (strong positive)") {
    val (g, src) = chainGraph(40)
    run("chain-s31-k1", g, src, repChainNfa(14), crossCheck = true)
  }

  test("p2 chain S127 k~1 (strong positive)") {
    val (g, src) = chainGraph(140)
    run("chain-s127-k1", g, src, repChainNfa(62))
  }

  test("p2 chain S255 k~1 (strong positive)") {
    val (g, src) = chainGraph(280)
    run("chain-s255-k1", g, src, repChainNfa(126))
  }

  test("p2 grid S19 deep branching (positive)") {
    val (g, src) = gridGraph(10, 10)
    run("grid-s19", g, src, repChainNfa(8), maxDepth = -1, k = 1)
  }

  test("p2 branch B2U8 occupancy ~9% (sparse intermediate)") {
    val (g, src) = diamondGraph(2, 3)
    run("branch-occ09", g, src, branchNfa(2, 8), crossCheck = true)
  }

  test("p2 branch B3U3 occupancy ~21% (intermediate)") {
    val (g, src) = diamondGraph(2, 3)
    run("branch-occ21", g, src, branchNfa(3, 3), crossCheck = true)
  }

  test("p2 branch B4U2 occupancy ~29% (crossover)") {
    val (g, src) = diamondGraph(2, 2)
    run("branch-occ29", g, src, branchNfa(4, 2))
  }

  test("p2 branch B4U0 occupancy ~67% (dense negative)") {
    val (g, src) = diamondGraph(2, 2)
    run("branch-occ67", g, src, branchNfa(4, 0))
  }

  test("p2 branch B6U6 occupancy ~43% (crossover)") {
    val (g, src) = diamondGraph(1, 2)
    run("branch-occ43", g, src, branchNfa(6, 6))
  }

  test("p2 branch B8U24 S34 k8 (big intermediate)") {
    val (g, src) = diamondGraph(1, 3)
    run("branch-big-k8", g, src, branchNfa(8, 24))
  }

  test("p2 branch B16U0 S18 k16 (dense negative)") {
    val (g, src) = diamondGraph(1, 2)
    run("branch-dense89", g, src, branchNfa(16, 0))
  }

  test("p2 tiny S2 (negative control)") {
    val (g, src) = chainGraph(10)
    run("tiny-s2", g, src, nfa((() |> ())), crossCheck = true)
  }

  test("p2 tiny S4 (negative control)") {
    val (g, src) = chainGraph(10)
    run("tiny-s4", g, src, nfa("s" |> ("a" --> "b") |> "t"), crossCheck = true)
  }

  test("p2 tiny S7 (negative control)") {
    val (g, src) = chainGraph(10)
    run("tiny-s7", g, src, repChainNfa(2), crossCheck = true)
  }

  test("p2 star fanout64 S66 lookup stress (sparse, high g/i)") {
    val (g, src) = starGraph(64)
    run("star-lookup-stress", g, src, fanoutNfa(32))
  }

  test("p2 clustered ids K4 S18 (range-positive intent)") {
    val (g, src) = diamondGraph(1, 1)
    run("locality-clustered", g, src, clusteredNfa(), crossCheck = true)
  }

  test("p2 dispersed ids K4 S18 (range-negative intent)") {
    val (g, src) = diamondGraph(1, 1)
    run("locality-dispersed", g, src, dispersedNfa(), crossCheck = true)
  }

  test("p2 bidirectional chain S31 (backward role)") {
    // 15-node line: exactly the 14-rel reach of repChainNfa(14), so the full-chain path ends at the target.
    val b = InMemoryGraph.builder
    val nodes = b.line(14)
    val g = b.build()
    run("bidir-chain-s31", g, nodes.head, repChainNfa(14),
      searchMode = SearchMode.Bidirectional, intoTarget = nodes.last)
  }

  test("p2 bidirectional standard NFA control (backward role)") {
    val b = InMemoryGraph.builder
    val nodes = b.line(19)
    val g = b.build()
    run("bidir-standard", g, nodes.head, `(s) ((a)-->(b))* (t)`,
      searchMode = SearchMode.Bidirectional, intoTarget = nodes.last)
  }

  test("p2 unidirectional into-target control (same graph/NFA as bidir)") {
    val b = InMemoryGraph.builder
    val nodes = b.line(14)
    val g = b.build()
    run("unidir-into-s31", g, nodes.head, repChainNfa(14),
      searchMode = SearchMode.Unidirectional, intoTarget = nodes.last)
  }

  // ---------- Part I large-k falsification (final qualification) ----------

  test("p2 large-k k32 S34 (S approx k)") {
    val (g, src) = fanoutLayerGraph(2, 2)
    run("largek-k32-s34", g, src, branchNfa(32, 0))
  }

  test("p2 large-k k32 S258 (S much larger than k)") {
    val (g, src) = fanoutLayerGraph(2, 2)
    run("largek-k32-s258", g, src, branchNfa(32, 224))
  }

  test("p2 large-k k64 S66 (S approx k)") {
    val (g, src) = fanoutLayerGraph(2, 2)
    run("largek-k64-s66", g, src, branchNfa(64, 0))
  }

  test("p2 large-k k64 S514 (S much larger than k)") {
    val (g, src) = fanoutLayerGraph(2, 2)
    run("largek-k64-s514", g, src, branchNfa(64, 448))
  }

  test("p2 large-k k128 S130 (S approx k)") {
    val (g, src) = fanoutLayerGraph(2, 2)
    run("largek-k128-s130", g, src, branchNfa(128, 0))
  }

  test("p2 large-k k128 S1026 (S much larger than k)") {
    val (g, src) = fanoutLayerGraph(2, 2)
    run("largek-k128-s1026", g, src, branchNfa(128, 896))
  }

  test("p2 large-k k256 S258 (S approx k)") {
    val (g, src) = fanoutLayerGraph(2, 2)
    run("largek-k256-s258", g, src, branchNfa(256, 0))
  }

  test("p2 hostile k32 F8 (high exact-lookup pressure)") {
    val (g, src) = fanoutLayerGraph(2, 8)
    run("hostile-k32-f8", g, src, branchNfa(32, 0))
  }

  test("p2 hostile k64 F8 (high exact-lookup pressure)") {
    val (g, src) = fanoutLayerGraph(2, 8)
    run("hostile-k64-f8", g, src, branchNfa(64, 0))
  }

  test("p2 hostile k128 F8 (high exact-lookup pressure)") {
    val (g, src) = fanoutLayerGraph(2, 8)
    run("hostile-k128-f8", g, src, branchNfa(128, 0))
  }

  test("p2 deep-repeat merge pressure (20x20 grid, 14-rel reach)") {
    val (g, src) = gridGraph(20, 20)
    run("grid-deep-merge-s31", g, src, repChainNfa(14), maxDepth = -1, k = 3)
  }

  /** Active ids {1,2,3,4}: four matching branches created adjacently. S = 18. */
  private def clusteredNfa(): Nfa =
    nfa("clustered") { sb =>
      import org.neo4j.graphdb.Direction
      val s = sb.newState("s", isStartState = true)
      val actives = (0 until 4).map(_ => sb.newState("a"))
      // 12 filler states with non-matching rel type to inflate S with identical topology.
      val fillers = (0 until 12).map(_ => sb.newState("u"))
      val f = sb.newState("f", isFinalState = true)
      actives.foreach { a =>
        s.addRelationshipExpansion(a, types = Array(1), direction = Direction.OUTGOING)
        a.addRelationshipExpansion(f, types = Array(1), direction = Direction.OUTGOING)
      }
      fillers.foreach { u =>
        s.addRelationshipExpansion(u, types = Array(2), direction = Direction.OUTGOING)
        u.addRelationshipExpansion(f, types = Array(2), direction = Direction.OUTGOING)
      }
    }

  /** Active ids {1,5,9,13}: matching branches interleaved with filler branches. S = 18. */
  private def dispersedNfa(): Nfa =
    nfa("dispersed") { sb =>
      import org.neo4j.graphdb.Direction
      val s = sb.newState("s", isStartState = true)
      val f = sb.newState("f", isFinalState = true)
      for (_ <- 0 until 4) {
        val a = sb.newState("a")
        s.addRelationshipExpansion(a, types = Array(1), direction = Direction.OUTGOING)
        a.addRelationshipExpansion(f, types = Array(1), direction = Direction.OUTGOING)
        for (_ <- 0 until 3) {
          val u = sb.newState("u")
          s.addRelationshipExpansion(u, types = Array(2), direction = Direction.OUTGOING)
          u.addRelationshipExpansion(f, types = Array(2), direction = Direction.OUTGOING)
        }
      }
    }
}
