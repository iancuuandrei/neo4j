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
import org.neo4j.memory.HeapEstimator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * RESEARCH-ONLY P2 large-k falsification microbench. Must never enter production.
 *
 * Extends the v1 screening (which stopped at k=64) through k=256 to establish the theoretical
 * danger zone for the fixed sorted vector's linear lookup. Compares ONLY D (dense direct) vs V
 * (sorted compact vector). The V prototype is faithful to the production StateBucket: sorted
 * order, append fast-path, idempotent re-put, and early exit on lookup miss past the range.
 * (The v1 prototype lacked early exit and was therefore pessimistic on inside-misses.)
 *
 * NOT JMH: medians of 7 samples in the repository test harness. Single JVM. Results feed the
 * fixed-V-vs-adaptive decision; they do not prove end-to-end effects.
 */
class P2LargeKMicrobench extends RuntimeUtilTestSuite {

  private final class StateStub(val id: Int)
  private final class NodeStub(val state: StateStub)

  /** Faithful V prototype: sorted, append fast-path, early exit, idempotent re-put. */
  private final class Vec {
    var arr: Array[AnyRef] = new Array[AnyRef](4)
    var size: Int = 0
    private def ensure(n: Int): Unit =
      if (n > arr.length) {
        val grown = new Array[AnyRef](math.max(n, arr.length * 2))
        System.arraycopy(arr, 0, grown, 0, size)
        arr = grown
      }
    def get(id: Int): NodeStub = {
      var i = 0
      while (i < size) {
        val cid = arr(i).asInstanceOf[NodeStub].state.id
        if (cid == id) return arr(i).asInstanceOf[NodeStub]
        if (cid > id) return null // early exit: production StateBucket.get semantics
        i += 1
      }
      null
    }
    def put(ns: NodeStub): Unit = {
      val id = ns.state.id
      if (size > 0) {
        val last = arr(size - 1).asInstanceOf[NodeStub].state.id
        if (id > last) { ensure(size + 1); arr(size) = ns; size += 1; return }
        if (id == last) return // idempotent re-put
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

  private def outDir(): Path = {
    val root = Path.of(Option(System.getenv("PPBFS_ARTIFACTS")).getOrElse("D:/dev/neo4j-research/artifacts/ppbfs"))
    val dir = root.resolve("runs/p2-largek-v1")
    Files.createDirectories(dir)
    dir
  }

  test("p2 large-k danger zone") {
    val stamp = Instant.now().toString.replace(':', '-')
    val csv = outDir().resolve(s"largek-$stamp.csv")
    val out = new StringBuilder
    out.append("S,k,pattern,op,D_ns,V_ns\n")
    // (k, S-variants): S≈k, S≈2k, S>>k
    val configs = Seq(
      (16, Seq(16, 32, 128)),
      (32, Seq(32, 64, 256)),
      (64, Seq(64, 128, 512)),
      (128, Seq(128, 256, 1024)),
      (256, Seq(256, 512, 2048))
    )
    for ((k, ss) <- configs; s <- ss if k <= s; p <- Seq("low", "dispersed")) {
      val ids = makeIds(s, k, p)
      val first = ids(0)
      val mid = ids(k / 2)
      val last = ids(k - 1)
      val lo = ids.min
      val hi = ids.max
      out.append(cell(s, k, p, "hit-first", ids, d => d.get(first), v => v.get(first)))
      out.append(cell(s, k, p, "hit-mid", ids, d => d.get(mid), v => v.get(mid)))
      out.append(cell(s, k, p, "hit-last", ids, d => d.get(last), v => v.get(last)))
      // Miss cells only where a valid absent index exists in that position (never out of bounds).
      if (lo > 0)
        out.append(cell(s, k, p, "miss-below", ids, d => d.get(lo - 1), v => v.get(lo - 1)))
      val inside = insideMiss(ids)
      if (inside >= 0)
        out.append(cell(s, k, p, "miss-inside", ids, d => d.get(inside), v => v.get(inside)))
      if (hi < s - 1)
        out.append(cell(s, k, p, "miss-above", ids, d => d.get(hi + 1), v => v.get(hi + 1)))
      val missRef = if (hi < s - 1) hi + 1 else if (lo > 0) lo - 1 else -1
      if (missRef >= 0)
        out.append(cell(s, k, p, "mixed", ids, d => { d.get(first); d.get(missRef) }, v => { v.get(first); v.get(missRef) }))
      out.append(cellIter(s, k, p, ids))
      out.append(cellConstruct(s, k, p, ids))
    }
    Files.writeString(csv, out.toString, StandardCharsets.UTF_8)
    println(s"large-k cells=${out.toString.split("\n").length - 1} csv=$csv")
  }

  private def makeIds(s: Int, k: Int, pattern: String): Array[Int] = pattern match {
    case "low"       => (0 until k).toArray
    case "dispersed" => (0 until k).map(i => (i * (s / k) + (s / (2 * k))) % s).sorted.toArray
  }

  /** An absent id strictly inside [min, max] of the active set, or -1 if none exists. */
  private def insideMiss(ids: Array[Int]): Int = {
    val used = ids.toSet
    val lo = ids.min
    val hi = ids.max
    (lo + 1 until hi).find(!used.contains(_)).getOrElse(-1)
  }

  private def buildDense(s: Int, ids: Array[Int]): HeapTrackingArrayList[NodeStub] = {
    val d = HeapTrackingArrayList.newEmptyArrayList[NodeStub](s, mt)
    ids.foreach(i => d.set(i, new NodeStub(new StateStub(i))))
    d
  }

  private def buildVec(ids: Array[Int]): Vec = {
    val v = new Vec
    ids.foreach(i => v.put(new NodeStub(new StateStub(i))))
    v
  }

  private def medianNs(reps: Int)(op: Int => Any): Double = {
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

  private def repsFor(s: Int, k: Int): Int = math.max(2000, 200000 / math.max(k, 1))

  private def cell(
    s: Int,
    k: Int,
    p: String,
    op: String,
    ids: Array[Int],
    dop: HeapTrackingArrayList[NodeStub] => Any,
    vop: Vec => Any
  ): String = {
    val d = buildDense(s, ids)
    val v = buildVec(ids)
    val reps = repsFor(s, k)
    medianNs(20000)(_ => dop(d))
    medianNs(20000)(_ => vop(v))
    val dn = medianNs(reps)(_ => dop(d))
    val vn = medianNs(reps)(_ => vop(v))
    s"$s,$k,$p,$op,$dn,$vn\n"
  }

  private def cellIter(s: Int, k: Int, p: String, ids: Array[Int]): String = {
    val d = buildDense(s, ids)
    val v = buildVec(ids)
    var acc = 0L
    val reps = math.max(2000, 200000 / math.max(s, 1))
    val dn = medianNs(reps) { _ =>
      var c = 0
      var i = 0
      while (i < s) { if (d.get(i) != null) c += 1; i += 1 }
      acc += c
      c
    }
    val vn = medianNs(reps) { _ =>
      var c = 0
      v.foreachActive(_ => c += 1)
      acc += c
      c
    }
    if (acc == -1) println("unreachable")
    s"$s,$k,$p,iter,$dn,$vn\n"
  }

  private def cellConstruct(s: Int, k: Int, p: String, ids: Array[Int]): String = {
    val reps = math.max(500, 20000 / math.max(k, 1))
    val dn = medianNs(reps) { _ => buildDense(s, ids) }
    val vn = medianNs(reps) { _ => buildVec(ids) }
    s"$s,$k,$p,construct,$dn,$vn\n"
  }
}
