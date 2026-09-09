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
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder
import org.neo4j.memory.LocalMemoryTracker

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 * RESEARCH-ONLY P2 end-to-end timing screen. Must never enter a production or contribution branch.
 *
 * Warmed, self-oracle-checked PPBFS runs over larger synthetic workloads. Screening grade: one JVM,
 * sequential workloads, medians of 9 timed runs after 5 warmups. Compares clean B0 against a clean
 * candidate by running this same file in two worktrees; cross-branch oracle equality is checked by
 * the analyst (any oracle mismatch disqualifies the candidate). Select one workload with
 * -Dp2.workload=<id>. Writes raw CSV to PPBFS_ARTIFACTS/runs/p2-timing-screen-v1/.
 */
class P2TimingScreen extends RuntimeUtilTestSuite with PGPathPropagatingBFSTestBase {

  private val Warmups = 5
  private val Timed = 9

  private def outDir(): Path = {
    val root = Path.of(Option(System.getenv("PPBFS_ARTIFACTS")).getOrElse("D:/dev/neo4j-research/artifacts/ppbfs"))
    val dir = root.resolve("runs/p2-timing-screen-v1")
    Files.createDirectories(dir)
    dir
  }

  private case class Workload(
    id: String,
    graph: InMemoryGraph,
    source: Long,
    nfa: Nfa,
    maxDepth: Int = -1,
    k: Int = Int.MaxValue,
    intoTarget: Long = -1L,
    bidirectional: Boolean = false,
    repeats: Int = 1
  )

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

  private def fanoutNfa(branches: Int): Nfa =
    nfa(s"fanout-$branches") { sb =>
      val s = sb.newState("s", isStartState = true)
      for (i <- 0 until branches) {
        val f = sb.newState(if (i == 0) "f" else "g", isFinalState = i == 0)
        s.addRelationshipExpansion(f, types = Array(i + 1), direction = Direction.OUTGOING)
      }
    }

  private def branchNfa(matching: Int, nonMatching: Int): Nfa =
    nfa(s"branch-B${matching}U$nonMatching") { sb =>
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

  private def chainGraph(n: Int): (InMemoryGraph, Long) = {
    val b = InMemoryGraph.builder
    val nodes = b.line(n - 1)
    (b.build(), nodes.head)
  }

  private def starGraph(leaves: Int): (InMemoryGraph, Long, Long) = {
    val b = InMemoryGraph.builder
    val center = b.node()
    var firstLeaf = -1L
    (0 until leaves).foreach { _ =>
      val leaf = b.node()
      if (firstLeaf < 0) firstLeaf = leaf
      b.rel(center, leaf)
    }
    (b.build(), center, firstLeaf)
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
   * L1 (f nodes, complete bipartite) . An L0 node reached in B branch states has k=B actives,
   * each expanding over F rels, giving g/i ≈ B*F from a single bucket iteration.
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

  private def workloads(): Seq[Workload] = {
    val (c2000, cSrc) = chainGraph(2000)
    val (star2000, starSrc, starLeaf) = starGraph(2000)
    val (grid30, gridSrc) = gridGraph(30, 30)
    val (dia, diaSrc) = diamondGraph(3, 4)
    val (c500, c500Src) = chainGraph(500)
    val (h3dia, h3Src) = diamondGraph(4, 5)
    // out node of the diamond builder is not returned; rediscover: rebuild explicitly for into-target
    val h3b = InMemoryGraph.builder
    val h3source = h3b.node()
    var h3prev: Seq[Long] = Seq(h3source)
    for (_ <- 0 until 4) {
      val cur = (0 until 5).map(_ => h3b.node())
      for (a <- h3prev; c <- cur) h3b.rel(a, c)
      h3prev = cur
    }
    val h3target = h3b.node()
    h3prev.foreach(a => h3b.rel(a, h3target))
    val h3graph = h3b.build()
    // depth-2 variant so the 2-hop branch NFA reaches the target (H3 distances are short)
    val h3sb = InMemoryGraph.builder
    val h3ssource = h3sb.node()
    val h3mid = (0 until 5).map(_ => h3sb.node())
    h3mid.foreach(m => h3sb.rel(h3ssource, m))
    val h3starget = h3sb.node()
    h3mid.foreach(m => h3sb.rel(m, h3starget))
    val h3sgraph = h3sb.build()
    // Large-k falsification family (Part I): layered fanout graphs x branch NFAs.
    // Rows per workload = W*F*B traced paths; both variants pay tracing equally.
    val (fanW2F2, fanW2F2Src) = fanoutLayerGraph(2, 2)
    val (fanW2F8, fanW2F8Src) = fanoutLayerGraph(2, 8)
    val (grid20, grid20Src) = gridGraph(20, 20)
    Seq(
      Workload("chain2000-s255", c2000, cSrc, repChainNfa(126), repeats = 10),
      Workload("chain2000-s509", c2000, cSrc, repChainNfa(252), repeats = 10),
      Workload("chain2000-s31", c2000, cSrc, repChainNfa(14), repeats = 20),
      Workload("star2000-s33", star2000, starSrc, fanoutNfa(32), intoTarget = starLeaf, repeats = 20),
      Workload("grid30-s19", grid30, gridSrc, repChainNfa(8), k = 3, repeats = 5),
      // NOTE grid60-s63 removed 2026-09-09: InMemoryGraph.nodeRels scans O(E) per expansion,
      // infeasible for repeated timing in EVERY variant (B0 included). grid30 retains coverage.
      // H3 analog: S=5, k=3, deep reconvergent diamond (lookup-heavy small-NFA regime)
      Workload("h3analog-s5", h3dia, h3Src, branchNfa(3, 0), repeats = 10),
      // H3 analog, bidirectional into-target with k=2 like the real H3 query
      Workload(
        "h3analog-bidi-s5",
        h3graph,
        h3source,
        branchNfa(3, 0),
        k = 2,
        intoTarget = h3target,
        bidirectional = true,
        repeats = 10
      ),
      // depth-matched bidi analog: 2-hop NFA reaches the bound target (rows>0)
      Workload(
        "h3analog-bidi2-s5",
        h3sgraph,
        h3ssource,
        branchNfa(3, 0),
        k = 2,
        intoTarget = h3starget,
        bidirectional = true,
        repeats = 10
      ),
      Workload("diamond-dense-s18", dia, diaSrc, branchNfa(16, 0), repeats = 10),
      Workload("tiny-chain500-s4", c500, c500Src, nfa("s" |> ("a" --> "b") |> "t"), repeats = 30),
      // Large-k falsifiers: k = max actives at an L0 bucket; S = 2+B+U.
      Workload("k32-s34", fanW2F2, fanW2F2Src, branchNfa(32, 0), repeats = 10),
      Workload("k32-s258", fanW2F2, fanW2F2Src, branchNfa(32, 224), repeats = 10),
      Workload("k64-s66", fanW2F2, fanW2F2Src, branchNfa(64, 0), repeats = 10),
      Workload("k64-s514", fanW2F2, fanW2F2Src, branchNfa(64, 448), repeats = 5),
      Workload("k128-s130", fanW2F2, fanW2F2Src, branchNfa(128, 0), repeats = 5),
      Workload("k128-s1026", fanW2F2, fanW2F2Src, branchNfa(128, 896), repeats = 3),
      Workload("k256-s258", fanW2F2, fanW2F2Src, branchNfa(256, 0), repeats = 3),
      // Hostile high-g/i: F=8 fanout multiplies exact lookups per large-k bucket.
      Workload("hostile-k32-f8", fanW2F8, fanW2F8Src, branchNfa(32, 0), repeats = 5),
      Workload("hostile-k64-f8", fanW2F8, fanW2F8Src, branchNfa(64, 0), repeats = 3),
      Workload("hostile-k128-f8", fanW2F8, fanW2F8Src, branchNfa(128, 0), repeats = 2),
      // Deep-repeat merge pressure (C4 retired index activates at depth>=8 with revisits).
      Workload("grid-deep-merge-s31", grid20, grid20Src, repChainNfa(14), k = 3, repeats = 3)
    )
  }

  private def runOnce(w: Workload): (Long, Long, Long, String) = {
    val mt = new LocalMemoryTracker()
    // -Dp2.repeatsScale=N lengthens runs for profiler attribution (default 1).
    val scale = Option(System.getProperty("p2.repeatsScale")).map(_.toInt).getOrElse(1)
    val totalRepeats = w.repeats * scale
    val t0 = System.nanoTime()
    var rows = 0L
    var lenSum = 0L
    val perPath = new java.util.ArrayList[String]()
    var r = 0
    while (r < totalRepeats) {
      val fb0 = fixture()
        .withGraph(w.graph)
        .from(w.source)
        .withNfa(w.nfa)
        .withMaxDepth(w.maxDepth)
        .withK(w.k)
        .withMemoryTracker(mt)
      val fb = if (w.intoTarget != -1L)
        fb0.into(w.intoTarget, if (w.bidirectional) SearchMode.Bidirectional else SearchMode.Unidirectional)
      else fb0
      val iter = fb.build().asScala
      while (iter.hasNext) {
        val path = iter.next()
        rows += 1
        val ids = path.entities.map(_.id)
        lenSum += ids.size
        val md = MessageDigest.getInstance("SHA-256")
        ids.foreach(l => md.update(java.nio.ByteBuffer.allocate(8).putLong(l).array()))
        perPath.add(md.digest().map("%02x".format(_)).mkString)
      }
      r += 1
    }
    val t1 = System.nanoTime()
    import scala.jdk.CollectionConverters._
    val finalMd = MessageDigest.getInstance("SHA-256")
    perPath.asScala.sorted.foreach(h => finalMd.update(h.getBytes(StandardCharsets.UTF_8)))
    (t1 - t0, rows, mt.estimatedHeapMemory(), finalMd.digest().map("%02x".format(_)).mkString)
  }

  test("p2 timing screen") {
    val only = Option(System.getProperty("p2.workload"))
    val onlyMany = Option(System.getProperty("p2.workloads")).map(_.split(",").toSet)
    val all = workloads()
    val selected = (only, onlyMany) match {
      case (Some(id), _) => all.filter(_.id == id)
      case (_, Some(ids)) =>
        val missing = ids.filterNot(id => all.exists(_.id == id))
        assert(missing.isEmpty, s"unknown p2.workloads=$missing")
        all.filter(w => ids.contains(w.id))
      case _ =>
        // seeded shuffle defeats order effects across forks; default seed keeps screening deterministic
        val seed = Option(System.getProperty("p2.seed")).map(_.toLong).getOrElse(0L)
        new scala.util.Random(seed).shuffle(all)
    }
    assert(selected.nonEmpty, s"unknown p2.workload=$only")
    val variant = Option(System.getProperty("p2.variant")).getOrElse("unknown")
    val fork = Option(System.getProperty("p2.fork")).getOrElse("0")
    val stamp = java.time.Instant.now().toString.replace(':', '-')
    val csv = outDir().resolve(s"screen-$variant-fork$fork-$stamp.csv")
    val sb = new StringBuilder
    sb.append("variant,fork,workload,S,repeats,run,elapsed_ns,rows,tracked_bytes,hash\n")
    selected.foreach { w =>
      val probe = new PGStateBuilder
      w.nfa(probe)
      val s = probe.stateCount
      // warmup
      val warmOracles = (0 until Warmups).map(_ => { val r = runOnce(w); (r._2, r._4) })
      System.gc()
      // timed
      val timed = (0 until Timed).map(_ => { System.gc(); runOnce(w) })
      val all = warmOracles.map { case (rows, hash) => (0L, rows, 0L, hash) } ++ timed
      val ref = (all.head._2, all.head._4)
      assert(
        all.forall { case (_, rows, _, hash) => (rows, hash) == ref },
        s"oracle mismatch within $variant/${w.id}: $all"
      )
      timed.zipWithIndex.foreach { case ((ns, rows, tracked, hash), i) =>
        sb.append(s"$variant,$fork,${w.id},$s,${w.repeats},$i,$ns,$rows,$tracked,$hash\n")
      }
      val sorted = timed.map(_._1).sorted
      println(s"[$variant/${w.id}] S=$s median_ns=${sorted(sorted.size / 2)} rows=${ref._1} hash=${ref._2}")
    }
    Files.writeString(csv, sb.toString, StandardCharsets.UTF_8)
    println(s"screen csv=$csv")
  }
}
