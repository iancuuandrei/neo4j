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

import org.neo4j.collection.trackable.{HeapTrackingArrayList, HeapTrackingLongObjectHashMap}
import org.neo4j.cypher.internal.runtime.RuntimeUtilTestSuite
import org.neo4j.memory.EmptyMemoryTracker

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.collection.mutable.ArrayBuffer

/** Research-only structural crossover harness. It is excluded from any clean contribution branch. */
class C4StructuralCrossoverBenchmarkTest extends RuntimeUtilTestSuite {

  private val mt = EmptyMemoryTracker.INSTANCE
  private var sink = 0

  private def bucket(q: Int, marker: AnyRef): HeapTrackingArrayList[AnyRef] = {
    val result = HeapTrackingArrayList.newEmptyArrayList[AnyRef](q, mt)
    var i = 0
    while (i < q) {
      result.set(i, marker)
      i += 1
    }
    result
  }

  private def level(size: Int, q: Int, keyOffset: Long): HeapTrackingLongObjectHashMap[HeapTrackingArrayList[AnyRef]] = {
    val result = HeapTrackingLongObjectHashMap.createLongObjectHashMap[HeapTrackingArrayList[AnyRef]](mt, size)
    val marker = new Object
    var i = 0
    while (i < size) {
      result.put(keyOffset + i, bucket(q, marker))
      i += 1
    }
    result
  }

  private def medianNsPerOperation(samples: Int, operations: Int)(run: => Unit): Double = {
    val values = Array.fill(samples) {
      val start = System.nanoTime()
      run
      (System.nanoTime() - start).toDouble / operations
    }.sorted
    values(values.length / 2)
  }

  test("record C4 structural crossover") {
    assume(sys.props.contains("ppbfs.c4.micro.output"), "C4 structural calibration not requested")
    val output = Path.of(sys.props("ppbfs.c4.micro.output"))
    val qs = Seq(1, 2, 4, 8, 16, 32, 64)
    val sizes = Seq(16, 256, 4096)
    val historyDepths = Seq(1, 2, 4, 8, 16, 32)
    val lookupOperations = sys.props.get("ppbfs.c4.micro.lookups").fold(200000)(_.toInt)
    val samples = sys.props.get("ppbfs.c4.micro.samples").fold(7)(_.toInt)
    val rows = ArrayBuffer("operation,q,map_size,history_depth,operations,median_ns_per_operation")

    for {
      q <- qs
      size <- sizes
    } {
      val direct = level(size, q, 0)
      for (depth <- historyDepths) {
        val history = Array.tabulate(depth)(i => level(size, q, i.toLong * (size + 1)))
        for (_ <- 0 until 3) {
          var i = history.length - 1
          while (i >= 0) {
            if (history(i).get(-1L) != null) sink += 1
            i -= 1
          }
        }
        val missNs = medianNsPerOperation(samples, lookupOperations) {
          var operation = 0
          while (operation < lookupOperations) {
            var i = history.length - 1
            while (i >= 0) {
              if (history(i).get(-1L) != null) sink += 1
              i -= 1
            }
            operation += 1
          }
        }
        rows += Seq("history_miss", q, size, depth, lookupOperations, missNs).mkString(",")

        val oldestKey = 0L
        val hitNs = medianNsPerOperation(samples, lookupOperations) {
          var operation = 0
          while (operation < lookupOperations) {
            var i = history.length - 1
            var found: AnyRef = null
            while (i >= 0 && found == null) {
              val values = history(i).get(oldestKey)
              if (values != null) found = values.get(0)
              i -= 1
            }
            if (found != null) sink += 1
            operation += 1
          }
        }
        rows += Seq("history_oldest_hit", q, size, depth, lookupOperations, hitNs).mkString(",")
      }

      val directNs = medianNsPerOperation(samples, lookupOperations) {
        var operation = 0
        while (operation < lookupOperations) {
          if (direct.get((operation % size).toLong) != null) sink += 1
          operation += 1
        }
      }
      rows += Seq("direct_hit", q, size, 0, lookupOperations, directNs).mkString(",")

      val transferSamples = Array.fill(samples) {
        val source = level(size, q, size.toLong)
        val destination = level(size, q, 0)
        val start = System.nanoTime()
        source.forEachKeyValue((key, value) => destination.put(key, value))
        val elapsed = System.nanoTime() - start
        sink += destination.size()
        elapsed.toDouble / size
      }.sorted
      rows += Seq("bucket_transfer", q, size, 0, size, transferSamples(samples / 2)).mkString(",")

      val mergeSamples = Array.fill(samples) {
        val source = level(size, q, 0)
        val destination = HeapTrackingLongObjectHashMap
          .createLongObjectHashMap[HeapTrackingArrayList[AnyRef]](mt, size)
        var i = 0
        while (i < size) {
          destination.put(i.toLong, HeapTrackingArrayList.newEmptyArrayList[AnyRef](q, mt))
          i += 1
        }
        val start = System.nanoTime()
        source.forEachKeyValue { (key, incoming) =>
          val existing = destination.get(key)
          var state = 0
          while (state < q) {
            existing.set(state, incoming.get(state))
            state += 1
          }
        }
        val elapsed = System.nanoTime() - start
        sink += destination.size()
        elapsed.toDouble / size
      }.sorted
      rows += Seq("repeated_node_merge", q, size, 0, size, mergeSamples(samples / 2)).mkString(",")
    }

    Files.createDirectories(output.getParent)
    Files.writeString(output, rows.mkString("\n") + "\n", StandardCharsets.UTF_8)
    sink should be > 0
  }
}
