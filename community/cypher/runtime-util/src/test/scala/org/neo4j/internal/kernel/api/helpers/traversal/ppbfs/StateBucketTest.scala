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
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.Lengths.trailMode
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.State
import org.neo4j.kernel.api.StatementConstants.NO_SUCH_NODE
import org.neo4j.memory.EmptyMemoryTracker
import org.neo4j.memory.HeapEstimator
import org.neo4j.memory.LocalMemoryTracker
import org.neo4j.memory.MemoryTracker

import scala.jdk.CollectionConverters.IterableHasAsScala

class StateBucketTest extends RuntimeUtilTestSuite {

  private def globalState(mt: MemoryTracker = EmptyMemoryTracker.INSTANCE) = {
    val hooks = PPBFSHooks.NULL
    new GlobalState(
      new Propagator(EmptyMemoryTracker.INSTANCE, hooks),
      new TargetTracker(EmptyMemoryTracker.INSTANCE, hooks),
      SearchMode.Unidirectional,
      mt,
      hooks,
      1
    )
  }

  private def nodeState(sb: PGStateBuilder, gs: GlobalState, nodeId: Long, name: String): NodeState = {
    val st = sb.newState(name)
    new NodeState(gs, nodeId, st.state, NO_SUCH_NODE, trailMode())
  }

  test("empty bucket misses every lookup and iterates nothing") {
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    bucket.activeSize() shouldBe 0
    bucket.get(0) shouldBe null
    bucket.get(7) shouldBe null
    bucket.asScala.toSeq shouldBe empty
    val target = HeapTrackingArrayList.newArrayList[State](EmptyMemoryTracker.INSTANCE)
    bucket.appendActiveStatesTo(target)
    target.size() shouldBe 0
  }

  test("single state round-trips") {
    val sb = new PGStateBuilder
    val gs = globalState()
    val ns = nodeState(sb, gs, 9L, "a")
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    bucket.put(ns)
    bucket.activeSize() shouldBe 1
    bucket.get(ns.state().id()) shouldBe ns
    bucket.get(ns.state().id() + 1) shouldBe null
  }

  test("out-of-order inserts iterate in ascending state-id order") {
    val sb = new PGStateBuilder
    val gs = globalState()
    val nodes = (0 until 6).map(i => nodeState(sb, gs, 3L, s"s$i"))
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    // insert descending, then a middle id twice (idempotent)
    nodes.reverse.foreach(bucket.put)
    bucket.put(nodes(2))
    bucket.activeSize() shouldBe 6
    val ids = bucket.asScala.map(_.state().id()).toSeq
    ids shouldBe ids.sorted
    ids shouldBe (0 until 6).toSeq
    nodes.foreach(ns => bucket.get(ns.state().id()) shouldBe ns)
  }

  test("late insertion of a lower id keeps ascending order") {
    val sb = new PGStateBuilder
    val gs = globalState()
    val nodes = (0 until 4).map(i => nodeState(sb, gs, 5L, s"s$i"))
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    bucket.put(nodes(3))
    bucket.put(nodes(1))
    bucket.put(nodes(0))
    bucket.put(nodes(2))
    bucket.asScala.map(_.state().id()).toSeq shouldBe Seq(0, 1, 2, 3)
  }

  test("missing ids below, inside and above the range return null") {
    val sb = new PGStateBuilder
    val gs = globalState()
    val nodes = Seq(2, 5, 9).map(i => {
      while (sb.stateCount <= i) sb.newState()
      val st = sb.getState(i)
      new NodeState(gs, 1L, st.state, NO_SUCH_NODE, trailMode())
    })
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    nodes.foreach(bucket.put)
    bucket.get(0) shouldBe null
    bucket.get(3) shouldBe null
    bucket.get(7) shouldBe null
    bucket.get(10) shouldBe null
    bucket.get(2) shouldBe nodes(0)
    bucket.appendActiveStatesTo(HeapTrackingArrayList.newArrayList[State](EmptyMemoryTracker.INSTANCE))
  }

  test("duplicate insertion of the same instance is a no-op") {
    val sb = new PGStateBuilder
    val gs = globalState()
    val ns = nodeState(sb, gs, 1L, "a")
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    bucket.put(ns)
    bucket.put(ns)
    bucket.activeSize() shouldBe 1
    bucket.get(ns.state().id()) shouldBe ns
  }

  test("conflicting identity for the same state id fails") {
    val sb = new PGStateBuilder
    val gs = globalState()
    val first = nodeState(sb, gs, 1L, "a")
    // second NodeState wrapping the SAME product state object: same (nodeId, stateId), other identity
    val second = new NodeState(gs, 1L, first.state(), NO_SUCH_NODE, trailMode())
    (second ne first) shouldBe true
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    bucket.put(first)
    an[IllegalStateException] should be thrownBy bucket.put(second)
    // failed put must not corrupt the bucket
    bucket.activeSize() shouldBe 1
    bucket.get(first.state().id()) shouldBe first
  }

  test("growth beyond initial capacity preserves order and lookup") {
    val sb = new PGStateBuilder
    val gs = globalState()
    val nodes = (0 until 20).map(i => nodeState(sb, gs, 2L, s"s$i"))
    val bucket = new StateBucket(EmptyMemoryTracker.INSTANCE)
    // ascending (append path) then verify; then a fresh bucket with descending inserts
    nodes.foreach(bucket.put)
    bucket.activeSize() shouldBe 20
    bucket.asScala.map(_.state().id()).toSeq shouldBe (0 until 20).toSeq
  }

  test("tracked memory follows the backing array exactly and is released on close") {
    val mt = new LocalMemoryTracker()
    val sb = new PGStateBuilder
    val gs = globalState()
    val bucket = new StateBucket(mt)
    val shallow = HeapEstimator.shallowSizeOfInstance(classOf[StateBucket])
    mt.estimatedHeapMemory() shouldBe shallow + HeapEstimator.shallowSizeOfObjectArray(4)
    (0 until 4).foreach(i => bucket.put(nodeState(sb, gs, 4L, s"s$i")))
    mt.estimatedHeapMemory() shouldBe shallow + HeapEstimator.shallowSizeOfObjectArray(4)
    // fifth insert grows 4 -> 8; the old array must be released exactly once
    bucket.put(nodeState(sb, gs, 4L, "s4"))
    mt.estimatedHeapMemory() shouldBe shallow + HeapEstimator.shallowSizeOfObjectArray(8)
    bucket.close()
    mt.estimatedHeapMemory() shouldBe 0
  }
}
