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
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.TraversalDirection.BACKWARD
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.TraversalDirection.FORWARD
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder
import org.neo4j.kernel.api.StatementConstants.NO_SUCH_NODE
import org.neo4j.memory.EmptyMemoryTracker
import org.neo4j.memory.LocalMemoryTracker

class FoundNodesTest extends RuntimeUtilTestSuite {

  private val stateBuilder = new PGStateBuilder
  private val state0 = stateBuilder.newState().state
  private val state1 = stateBuilder.newState().state

  test("canonical lookup retains identity after frontier retirement") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2)
    val first = nodeState(11, state0)
    val second = nodeState(22, state1)

    found.openBuffer()
    found.addToBuffer(first)
    found.commitBuffer(FORWARD)
    found.openBuffer()
    found.addToBuffer(second)
    found.commitBuffer(FORWARD)

    found.get(11, state0.id()) should be theSameInstanceAs first
    found.get(22, state1.id()) should be theSameInstanceAs second
    found.frontier(FORWARD).get(11) shouldBe null
    found.close()
  }

  test("canonical lookup distinguishes node and state dimensions") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2)
    val node11State0 = nodeState(11, state0)
    val node11State1 = nodeState(11, state1)
    val node22State0 = nodeState(22, state0)

    found.openBuffer()
    found.addToBuffer(node11State0)
    found.addToBuffer(node11State1)
    found.addToBuffer(node22State0)

    found.get(11, state0.id()) should be theSameInstanceAs node11State0
    found.get(11, state1.id()) should be theSameInstanceAs node11State1
    found.get(22, state0.id()) should be theSameInstanceAs node22State0
    found.get(22, state1.id()) shouldBe null
    found.close()
  }

  test("canonical lookup is shared by bidirectional frontiers") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Bidirectional, 2)
    val forward = nodeState(11, state0)
    val backward = nodeState(22, state1)

    found.openBuffer()
    found.addToBuffer(forward)
    found.commitBuffer(FORWARD)
    found.openBuffer()
    found.addToBuffer(backward)
    found.commitBuffer(BACKWARD)

    found.get(11, state0.id()) should be theSameInstanceAs forward
    found.get(22, state1.id()) should be theSameInstanceAs backward
    found.close()
  }

  test("canonical key cannot be replaced by another NodeState instance") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2)
    val first = nodeState(11, state0)

    found.openBuffer()
    found.addToBuffer(first)
    an[IllegalStateException] should be thrownBy found.addToBuffer(nodeState(11, state0))
    found.get(11, state0.id()) should be theSameInstanceAs first
    found.close()
  }

  test("retirement releases frontier memory and close releases all structural memory") {
    val tracker = new LocalMemoryTracker()
    val found = new FoundNodes(tracker, SearchMode.Unidirectional, 2)
    val first = nodeState(11, state0)

    found.openBuffer()
    found.addToBuffer(first)
    found.commitBuffer(FORWARD)
    val beforeRetirement = tracker.estimatedHeapMemory()
    found.openBuffer()
    found.commitBuffer(FORWARD)
    val afterRetirement = tracker.estimatedHeapMemory()

    afterRetirement should be < beforeRetirement
    found.get(11, state0.id()) should be theSameInstanceAs first
    found.close()
    tracker.estimatedHeapMemory() shouldBe 0L
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
