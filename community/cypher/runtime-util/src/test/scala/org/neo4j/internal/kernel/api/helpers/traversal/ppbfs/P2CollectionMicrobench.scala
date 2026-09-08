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
import org.neo4j.collection.trackable.HeapTrackingIntObjectHashMap
import org.neo4j.cypher.internal.runtime.RuntimeUtilTestSuite
import org.neo4j.memory.EmptyMemoryTracker
import org.neo4j.memory.HeapEstimator

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * RESEARCH-ONLY P2 collection screening. Must never enter a production or contribution branch.
 *
 * Compares prototype bucket representations on synthetic operation mixes to eliminate bad
 * candidates and estimate the dense crossover. This is NOT JMH: medians of repeated samples
 * inside the repository test harness, following the C4 microbenchmark precedent. Uses real
 * Neo4j tracked collections for D/DA/H and a plain-array sorted-vector prototype for V with
 * stub payloads that preserve the NodeState->State id-indirection depth of the real lookup.
 *
 * Limitations: single JVM fork; JIT can optimize tight loops; EmptyMemoryTracker omits tracker
 * arithmetic; stubs omit NodeState field footprint (refs only — lookup/iteration touch refs and
 * one int per payload, same as production). Results select candidates for end-to-end falsification;
 * they do not prove end-to-end wins.
 */
class P2CollectionMicrobench extends RuntimeUtilTestSuite {

  // ---- stubs preserving indirection depth: bucket -> NodeState -> State -> id ----
  private final class StateStub(val id: Int)
  private final class NodeStub(val state: StateStub)

  // ---- prototype V: sorted compact vector, linear lookup ----
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
        val ns = arr(i).asInstanceOf[NodeStub]
        if (ns.state.id == id) return ns
        i += 1
      }
      null
    }
    def put(ns: NodeStub): Unit = {
      val id = ns.state.id
      var i = 0
      while (i < size && arr(i).asInstanceOf[NodeStub].state.id < id) i += 1
      if (i < size && arr(i).asInstanceOf[NodeStub].state.id == id) return // idempotent re-put
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

  private def outDir(): Path = {
    val root = Path.of(Option(System.getenv("PPBFS_ARTIFACTS")).getOrElse("D:/dev/neo4j-research/artifacts/ppbfs"))
    val dir = root.resolve("runs/p2-microbench-v1")
    Files.createDirectories(dir)
    dir
  }

  test("p2 memory constants probe") {
    val sb = new StringBuilder
    sb.append(s"java=${System.getProperty("java.version")} ${System.getProperty("java.vendor")}\n")
    sb.append(s"HeapTrackingArrayList.shallow=${HeapEstimator.shallowSizeOfInstance(classOf[HeapTrackingArrayList[_]])}\n")
    sb.append(s"HeapTrackingIntObjectHashMap.shallow=${HeapEstimator.shallowSizeOfInstance(classOf[HeapTrackingIntObjectHashMap[_]])}\n")
    for (n <- Seq(1, 2, 3, 4, 6, 8, 16, 18, 32, 34, 64, 128, 255, 256, 512))
      sb.append(s"objectArray[$n]=${HeapEstimator.shallowSizeOfObjectArray(n)}\n")
    // Tracked construction sizes as production accounts them.
    for (s <- Seq(2, 4, 7, 8, 12, 18, 33, 34, 64, 127, 128, 255, 256, 512)) {
      val t = EmptyMemoryTracker.INSTANCE
      val d = HeapTrackingArrayList.newEmptyArrayList[AnyRef](s, t)
      d.set(0, new Object())
      sb.append(s"denseBucket[S=$s] ok\n")
    }
    Files.writeString(outDir().resolve("memory-constants.txt"), sb.toString, StandardCharsets.UTF_8)
    println(sb.toString)
  }

  test("p2 collection screening") {
    val stamp = Instant.now().toString.replace(':', '-')
    val csv = outDir().resolve(s"screening-$stamp.csv")
    val out = new StringBuilder
    out.append("S,k,pattern,op,D_ns,V_ns,DA_ns,H_ns\n")
    val SValues = Seq(4, 8, 32, 128, 512)
    val KValues = Seq(1, 2, 4, 8, 16, 32, 64)
    val patterns = Seq("low", "high", "dispersed")
    for (s <- SValues; k <- KValues if k <= s; p <- patterns) {
      val ids = makeIds(s, k, p)
      out.append(row(s, k, p, "hit", ids, ids(0), -1))
      // miss/insert are degenerate when the bucket is full (no absent id); skip, do not fake.
      if (k < s) {
        out.append(row(s, k, p, "miss", ids, -1, absentId(s, k, p)))
        out.append(row(s, k, p, "insert", ids, -4, -4))
      }
      out.append(row(s, k, p, "iter", ids, -2, -2))
      out.append(row(s, k, p, "construct", ids, -3, -3))
      out.append(row(s, k, p, "merge", ids, -5, -5))
    }
    Files.writeString(csv, out.toString, StandardCharsets.UTF_8)
    println(s"screening rows=${out.toString.split("\n").length - 1} csv=$csv")
  }

  private def makeIds(s: Int, k: Int, pattern: String): Array[Int] = pattern match {
    case "low"        => (0 until k).toArray
    case "high"       => (s - k until s).toArray
    case "dispersed"  => (0 until k).map(i => (i * (s / math.max(k, 1)) + (s / (2 * math.max(k, 1)))) % s).sorted.toArray
  }

  private def absentId(s: Int, k: Int, pattern: String): Int = {
    val used = makeIds(s, k, pattern).toSet
    (0 until s).find(!used.contains(_)).getOrElse(-1)
  }

  private val mt = EmptyMemoryTracker.INSTANCE

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

  private def buildDA(s: Int, ids: Array[Int]): (HeapTrackingArrayList[NodeStub], Array[Int]) = {
    (buildDense(s, ids), ids.clone())
  }

  private def buildHash(ids: Array[Int]): HeapTrackingIntObjectHashMap[NodeStub] = {
    val h = HeapTrackingIntObjectHashMap.createIntObjectHashMap[NodeStub](mt)
    ids.foreach(i => h.put(i, new NodeStub(new StateStub(i))))
    h
  }

  /** Median ns/op over 7 samples; reps scaled so a sample takes ~0.5-2 ms. */
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

  private def row(s: Int, k: Int, p: String, op: String, ids: Array[Int], hitId: Int, extra: Int): String = {
    val warm = 20000
    op match {
      case "hit" =>
        val d = buildDense(s, ids); val v = buildVec(ids)
        val (da, _) = buildDA(s, ids); val h = buildHash(ids)
        val id = hitId
        medianNs(warm)(_ => d.get(id)); medianNs(warm)(_ => v.get(id))
        val dn = medianNs(100000)(_ => d.get(id))
        val vn = medianNs(100000)(_ => v.get(id))
        val dan = medianNs(100000)(_ => da.get(id))
        val hn = medianNs(100000)(_ => h.get(id))
        s"$s,$k,$p,hit,$dn,$vn,$dan,$hn\n"
      case "miss" =>
        val d = buildDense(s, ids); val v = buildVec(ids)
        val (da, _) = buildDA(s, ids); val h = buildHash(ids)
        val id = extra
        val dn = medianNs(100000)(_ => d.get(id))
        val vn = medianNs(100000)(_ => v.get(id))
        val dan = medianNs(100000)(_ => da.get(id))
        val hn = medianNs(100000)(_ => h.get(id))
        s"$s,$k,$p,miss,$dn,$vn,$dan,$hn\n"
      case "iter" =>
        val d = buildDense(s, ids); val v = buildVec(ids)
        val (da, act) = buildDA(s, ids); val h = buildHash(ids)
        var acc = 0L
        val dn = medianNs(20000) { _ =>
          var c = 0; var i = 0
          while (i < s) { if (d.get(i) != null) c += 1; i += 1 }
          acc += c; c
        }
        val vn = medianNs(20000) { _ => var c = 0; v.foreachActive(_ => c += 1); acc += c; c }
        val dan = medianNs(20000) { _ =>
          var c = 0; var i = 0
          while (i < act.length) { if (da.get(act(i)) != null) c += 1; i += 1 }
          acc += c; c
        }
        val hn = medianNs(20000) { _ =>
          var c = 0
          val it = h.values().iterator()
          while (it.hasNext) { if (it.next() != null) c += 1 }
          acc += c; c
        }
        if (acc == -1) println("unreachable")
        s"$s,$k,$p,iter,$dn,$vn,$dan,$hn\n"
      case "construct" =>
        val dn = medianNs(20000) { _ => buildDense(s, ids) }
        val vn = medianNs(20000) { _ => buildVec(ids) }
        val dan = medianNs(20000) { _ => buildDA(s, ids) }
        val hn = medianNs(20000) { _ => buildHash(ids) }
        s"$s,$k,$p,construct,$dn,$vn,$dan,$hn\n"
      case "insert" =>
        // late insertion of one absent id into a built bucket (sorted position varies by pattern)
        val late = absentId(s, k, p)
        val dn = medianNs(20000) { _ => val d = buildDense(s, ids); d.set(late, new NodeStub(new StateStub(late))) }
        val vn = medianNs(20000) { _ => val v = buildVec(ids); v.put(new NodeStub(new StateStub(late))) }
        val dan = medianNs(20000) { _ =>
          val (da, act) = buildDA(s, ids)
          da.set(late, new NodeStub(new StateStub(late))); act :+ late
        }
        val hn = medianNs(20000) { _ => val h = buildHash(ids); h.put(late, new NodeStub(new StateStub(late))) }
        s"$s,$k,$p,insert,$dn,$vn,$dan,$hn\n"
      case "merge" =>
        val ids2 = makeIds(s, math.min(s, k + 1), if (p == "low") "high" else "low")
        val dn = medianNs(5000) { _ =>
          val d = buildDense(s, ids)
          ids2.foreach(i => if (d.get(i) == null) d.set(i, new NodeStub(new StateStub(i))))
          d
        }
        val vn = medianNs(5000) { _ =>
          val v = buildVec(ids)
          val w = buildVec(ids2)
          w.foreachActive(v.put)
          v
        }
        val dan = medianNs(5000) { _ =>
          val (da, act) = buildDA(s, ids)
          ids2.foreach(i => if (da.get(i) == null) da.set(i, new NodeStub(new StateStub(i))))
          da
        }
        val hn = medianNs(5000) { _ =>
          val h = buildHash(ids)
          ids2.foreach(i => if (h.get(i) == null) h.put(i, new NodeStub(new StateStub(i))))
          h
        }
        s"$s,$k,$p,merge,$dn,$vn,$dan,$hn\n"
    }
  }
}
