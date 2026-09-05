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
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.Lengths.trailMode
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.TraversalDirection.{BACKWARD, FORWARD}
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder
import org.neo4j.kernel.api.StatementConstants.NO_SUCH_NODE
import org.neo4j.memory.{EmptyMemoryTracker, LocalMemoryTracker}

class FoundNodesC4Test extends RuntimeUtilTestSuite {

  private val stateBuilder = new PGStateBuilder
  private val state0 = stateBuilder.newState().state
  private val state1 = stateBuilder.newState().state

  test("shallow history remains baseline and does not activate") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    val states = (0L to FoundNodes.ACTIVATION_HISTORY_DEPTH.toLong).map(id => nodeState(id, state0))

    states.foreach(state => promote(found, state, FORWARD))

    found.activated() shouldBe false
    found.frozenHistorySize() shouldBe FoundNodes.ACTIVATION_HISTORY_DEPTH
    states.foreach(state => found.get(state.id(), state0.id()) should be theSameInstanceAs state)
    found.close()
  }

  test("activation freezes prefix once and transfers first retiring map") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    val prefix = (0L to FoundNodes.ACTIVATION_HISTORY_DEPTH.toLong).map(id => nodeState(id, state0))
    prefix.foreach(state => promote(found, state, FORWARD))
    val retiringBucket = found.frontier(FORWARD).get(prefix.last.id())
    val next = nodeState(100, state0)

    promote(found, next, FORWARD)

    found.activated() shouldBe true
    found.frozenHistorySize() shouldBe FoundNodes.ACTIVATION_HISTORY_DEPTH
    found.retiredBucket(prefix.last.id()) should be theSameInstanceAs retiringBucket
    found.transferredBuckets() shouldBe 1L
    prefix.foreach(state => found.get(state.id(), state0.id()) should be theSameInstanceAs state)

    (101L to 104L).foreach(id => promote(found, nodeState(id, state0), FORWARD))
    found.frozenHistorySize() shouldBe FoundNodes.ACTIVATION_HISTORY_DEPTH
    found.activated() shouldBe true
    found.close()
  }

  test("post-activation repeated node merges states without replacing identity") {
    val tracker = new LocalMemoryTracker()
    val found = new FoundNodes(tracker, SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    activate(found)
    val first = nodeState(500, state0)
    val second = nodeState(500, state1)

    promote(found, first, FORWARD)
    promote(found, nodeState(501, state0), FORWARD)
    val retainedBucket = found.retiredBucket(500)
    promote(found, second, FORWARD)
    promote(found, nodeState(502, state0), FORWARD)

    found.retiredBucket(500) should be theSameInstanceAs retainedBucket
    found.get(500, state0.id()) should be theSameInstanceAs first
    found.get(500, state1.id()) should be theSameInstanceAs second
    found.mergedBuckets() should be >= 1L
    found.close()
    tracker.estimatedHeapMemory() shouldBe 0L
  }

  test("bidirectional retirement shares one canonical identity domain") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Bidirectional, 2, PPBFSHooks.NULL)
    var id = 0L
    while (found.frozenHistorySize() < FoundNodes.ACTIVATION_HISTORY_DEPTH) {
      promote(found, nodeState(id, state0), if (id % 2 == 0) FORWARD else BACKWARD)
      id += 1
    }
    while (!found.activated()) {
      promote(found, nodeState(id, state0), if (id % 2 == 0) FORWARD else BACKWARD)
      id += 1
    }
    val shared = nodeState(900, state0)
    promote(found, shared, FORWARD)
    promote(found, nodeState(901, state0), FORWARD)

    found.get(900, state0.id()) should be theSameInstanceAs shared
    found.close()
  }

  test("canonical key cannot be replaced in an active bucket") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    val first = nodeState(11, state0)
    found.openBuffer()
    found.addToBuffer(first)

    an[IllegalStateException] should be thrownBy found.addToBuffer(nodeState(11, state0))
    found.get(11, state0.id()) should be theSameInstanceAs first
    found.close()
  }

  test("close before and after activation releases all structural memory") {
    val shallowTracker = new LocalMemoryTracker()
    val shallow = new FoundNodes(shallowTracker, SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    promote(shallow, nodeState(1, state0), FORWARD)
    shallow.close()
    shallowTracker.estimatedHeapMemory() shouldBe 0L

    val deepTracker = new LocalMemoryTracker()
    val deep = new FoundNodes(deepTracker, SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    activate(deep)
    promote(deep, nodeState(1000, state0), FORWARD)
    deep.close()
    deepTracker.estimatedHeapMemory() shouldBe 0L
  }

  private def activate(found: FoundNodes): Unit = {
    var id = 0L
    while (!found.activated()) {
      promote(found, nodeState(id, state0), FORWARD)
      id += 1
    }
  }

  private def promote(found: FoundNodes, state: NodeState, direction: TraversalDirection): Unit = {
    found.openBuffer()
    found.addToBuffer(state)
    found.commitBuffer(direction)
  }

  private def nodeState(nodeId: Long, state: org.neo4j.internal.kernel.api.helpers.traversal.productgraph.State) =
    new NodeState(globalState(), nodeId, state, NO_SUCH_NODE, trailMode())

  private def globalState() = {
    val hooks = PPBFSHooks.NULL
    new GlobalState(
      new Propagator(EmptyMemoryTracker.INSTANCE, hooks),
      new TargetTracker(EmptyMemoryTracker.INSTANCE, hooks),
      SearchMode.Unidirectional,
      EmptyMemoryTracker.INSTANCE,
      hooks,
      1
    )
  }
}
