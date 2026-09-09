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

import org.neo4j.collection.trackable.HeapTrackingArrayList
import org.neo4j.cypher.internal.runtime.RuntimeUtilTestSuite
import org.neo4j.memory.EmptyMemoryTracker

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * RESEARCH-ONLY C4 merge audit. Must never enter production.
 *
 * Compares the two C4 retired-bucket merge shapes with identical stub payloads that preserve the
 * production NodeState->State id-indirection depth (two derefs per comparison):
 * - dense: exact replica of C4 FoundNodes.mergeBuckets — O(S) slot scan with identity guard;
 * - reput: the C4+P2 shape — `for (s : incoming) existing.put(s)` with the production-faithful
 *   sorted vector (sorted order, append fast-path, idempotent re-put, early exit).
 * Both sides rebuild bucket pairs per rep from shared stub payloads, so only merge work is timed.
 * The end-to-end arbiter is grid-deep-merge-s31 on C4 vs C4+P2 (real code, real merges).
 * NOT JMH: medians of 7 samples, single JVM.
 */
class P2MergeMicrobench extends RuntimeUtilTestSuite {

  private final class StateStub(val id: Int)
  private final class NodeStub(val state: StateStub)

  /** Production-faithful sorted vector (same structure as StateBucket.put/get). */
  private final class Vec {
    var arr: Array[AnyRef] = new Array[AnyRef](4)
    var size: Int = 0
    private def ensure(n: Int): Unit =
      if (n > arr.length) {
        val grown = new Array[AnyRef](math.max(n, arr.length * 2))
        System.arraycopy(arr, 0, grown, 0, size)
        arr = grown
      }
    def put(ns: NodeStub): Unit = {
      val id = ns.state.id
      if (size > 0) {
        val last = arr(size - 1).asInstanceOf[NodeStub].state.id
        if (id > last) { ensure(size + 1); arr(size) = ns; size += 1; return }
        if (id == last) return
      } else { arr(0) = ns; size = 1; return }
      var i = 0
      while (arr(i).asInstanceOf[NodeStub].state.id < id) i += 1
      if (arr(i).asInstanceOf[NodeStub].state.id == id) return
      ensure(size + 1)
      System.arraycopy(arr, i, arr, i + 1, size - i)
      arr(i) = ns
      size += 1
    }
    def foreachActive(f: NodeStub => Unit): Unit = {
      var i = 0
      while (i < size) { f(arr(i).asInstanceOf[NodeStub]); i += 1 }
    }
  }

  private val mt = EmptyMemoryTracker.INSTANCE

  // Exact replica of C4 FoundNodes.mergeBuckets, adapted to stubs.
  private def denseMerge(
    existing: HeapTrackingArrayList[NodeStub],
    incoming: HeapTrackingArrayList[NodeStub]
  ): Unit = {
    var stateId = 0
    while (stateId < incoming.size()) {
      val incomingState = incoming.get(stateId)
      if (incomingState != null) {
        val existingState = existing.get(stateId)
        assert(existingState == null || (existingState eq incomingState))
        if (existingState == null) existing.set(stateId, incomingState)
      }
      stateId += 1
    }
  }

  private def reputMerge(existing: Vec, incoming: Vec): Unit =
    incoming.foreachActive(existing.put)

  private def outDir(): Path = {
    val root = Path.of(Option(System.getenv("PPBFS_ARTIFACTS")).getOrElse("D:/dev/neo4j-research/artifacts/ppbfs"))
    val dir = root.resolve("runs/p2-merge-v1")
    Files.createDirectories(dir)
    dir
  }

  test("p2 c4 merge audit") {
    val stamp = Instant.now().toString.replace(':', '-')
    val csv = outDir().resolve(s"merge-$stamp.csv")
    val out = new StringBuilder
    out.append("S,k,overlap,layout,dense_ns,reput_ns\n")
    for (
      k <- Seq(4, 8, 16, 32, 64, 128);
      s <- Seq(2 * k, 8 * k);
      (overlap, layout) <- Seq(
        ("disjoint", "ascending"),
        ("disjoint", "below"), // worst realistic case: every put prepends (O(k^2) shifts)
        ("disjoint", "interleaved"),
        ("half", "ascending"),
        ("half", "interleaved"),
        ("full", "ascending"),
        ("full", "interleaved")
      )
    ) {
      out.append(mergeCell(s, k, overlap, layout))
    }
    Files.writeString(csv, out.toString, StandardCharsets.UTF_8)
    println(s"merge cells=${out.toString.split("\n").length - 1} csv=$csv")
  }

  private def existingIds(k: Int, layout: String): Array[Int] =
    if (layout == "ascending") (0 until k).toArray
    else if (layout == "below") (k until 2 * k).toArray
    else (0 until k).map(i => if (i % 2 == 0) i / 2 else k - 1 - i / 2).toArray

  private def incomingIds(k: Int, overlap: String, layout: String): Array[Int] = {
    val base = existingIds(k, layout)
    overlap match {
      case "disjoint" => if (layout == "below") (0 until k).toArray else base.map(_ + k)
      case "half"     => base.take(k / 2) ++ base.take(k / 2).map(_ + k)
      case "full"     => base.clone()
    }
  }

  private def medianNs(reps: Int)(op: Int => Any): Double = {
    // Retained for scalar cells; merge cells use timeIt (pre-built pairs, single use).
    val samples = new Array[Double](7)
    var r = 0
    while (r < 7) {
      val t0 = System.nanoTime()
      var i = 0
      var sink: Any = null
      while (i < reps) { sink = op(i); i += 1 }
      val t1 = System.nanoTime()
      if (sink == null && r == -1) println("unreachable")
      samples(r) = (t1 - t0).toDouble / reps
      r += 1
    }
    java.util.Arrays.sort(samples)
    samples(3)
  }

  private def mergeCell(s: Int, k: Int, overlap: String, layout: String): String = {
    val eIds = existingIds(k, layout)
    val iIds = incomingIds(k, overlap, layout)
    // Shared stub payloads per id (identity guard holds across rebuilds, as in production).
    val payloads = (0 until 2 * k).map(i => new NodeStub(new StateStub(i))).toArray
    val reps = math.max(100, 20000 / math.max(k, 1))
    // Pre-build pairs OUTSIDE the timed region; each pair is merged exactly once.
    // (A merge mutates `existing`, so pairs cannot be reused across samples.)
    def timeIt(build: () => Any, merge: Any => Unit): Double = {
      // Warmup with single-use pairs (merges mutate, so warmup pairs are throwaway).
      val warm = Array.fill(math.min(20000, reps * 4))(build())
      warm.foreach(merge)
      val samples = new Array[Double](7)
      var r = 0
      while (r < 7) {
        val pairs = Array.fill(reps)(build())
        val t0 = System.nanoTime()
        var i = 0
        while (i < reps) { merge(pairs(i)); i += 1 }
        val t1 = System.nanoTime()
        samples(r) = (t1 - t0).toDouble / reps
        r += 1
      }
      java.util.Arrays.sort(samples)
      samples(3)
    }
    val dn = timeIt(
      () => {
        val e = HeapTrackingArrayList.newEmptyArrayList[NodeStub](s, mt)
        val in = HeapTrackingArrayList.newEmptyArrayList[NodeStub](s, mt)
        eIds.foreach(i => e.set(i, payloads(i)))
        iIds.foreach(i => in.set(i, payloads(i)))
        (e, in)
      },
      { case (e: HeapTrackingArrayList[NodeStub], in: HeapTrackingArrayList[NodeStub]) => denseMerge(e, in) }
    )
    val vn = timeIt(
      () => {
        val e = new Vec
        val in = new Vec
        eIds.foreach(i => e.put(payloads(i)))
        iIds.foreach(i => in.put(payloads(i)))
        (e, in)
      },
      { case (e: Vec, in: Vec) => reputMerge(e, in) }
    )
    s"$s,$k,$overlap,$layout,$dn,$vn\n"
  }
}
