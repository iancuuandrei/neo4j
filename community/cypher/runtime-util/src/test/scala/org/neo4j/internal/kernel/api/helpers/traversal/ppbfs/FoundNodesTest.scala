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

  test("canonical lookup retains the same NodeState after its frontier is retired") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    val first = nodeState(11, state0)
    val second = nodeState(22, state1)

    found.get(11, state0.id()) shouldBe null

    found.openBuffer()
    found.addToBuffer(first)
    found.get(11, state0.id()) should be theSameInstanceAs first
    found.commitBuffer(FORWARD)

    found.openBuffer()
    found.addToBuffer(second)
    found.commitBuffer(FORWARD)

    found.get(11, state0.id()) should be theSameInstanceAs first
    found.get(22, state1.id()) should be theSameInstanceAs second
    found.frontier(FORWARD).get(11) shouldBe null
    found.close()
  }

  test("canonical lookup is shared by both bidirectional frontiers") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Bidirectional, 2, PPBFSHooks.NULL)
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
    found.frontier(FORWARD).get(11).get(state0.id()) should be theSameInstanceAs forward
    found.frontier(BACKWARD).get(22).get(state1.id()) should be theSameInstanceAs backward
    found.close()
  }

  test("a canonical key cannot be replaced by another NodeState instance") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    val first = nodeState(11, state0)
    val replacement = nodeState(11, state0)

    found.openBuffer()
    found.addToBuffer(first)
    an[IllegalStateException] should be thrownBy found.addToBuffer(replacement)

    found.get(11, state0.id()) should be theSameInstanceAs first
    found.close()
  }

  test("buffer lifecycle violations preserve existing exception behavior") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2, PPBFSHooks.NULL)

    an[IllegalStateException] should be thrownBy found.addToBuffer(nodeState(11, state0))
    an[IllegalStateException] should be thrownBy found.commitBuffer(FORWARD)
    found.openBuffer()
    an[IllegalStateException] should be thrownBy found.openBuffer()
    found.commitBuffer(FORWARD)
    found.close()
  }

  test("frontier retirement and close release all repository structural memory") {
    val tracker = new LocalMemoryTracker()
    val found = new FoundNodes(tracker, SearchMode.Unidirectional, 2, PPBFSHooks.NULL)
    val afterConstruction = tracker.estimatedHeapMemory()

    found.openBuffer()
    found.addToBuffer(nodeState(11, state0))
    found.commitBuffer(FORWARD)
    val withFirstCanonicalAndFrontier = tracker.estimatedHeapMemory()

    found.openBuffer()
    found.addToBuffer(nodeState(22, state1))
    val beforeRetirement = tracker.estimatedHeapMemory()
    found.commitBuffer(FORWARD)
    val afterRetirement = tracker.estimatedHeapMemory()

    afterConstruction should be > 0L
    withFirstCanonicalAndFrontier should be > 0L
    beforeRetirement should be > withFirstCanonicalAndFrontier
    // Commit retains both canonical entries but releases the retired frontier's structural allocation.
    afterRetirement should be > 0L
    afterRetirement should be < beforeRetirement

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
