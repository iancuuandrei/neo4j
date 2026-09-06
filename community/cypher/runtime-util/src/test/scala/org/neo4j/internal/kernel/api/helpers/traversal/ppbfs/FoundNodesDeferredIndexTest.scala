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

class FoundNodesDeferredIndexTest extends RuntimeUtilTestSuite {

  private val stateBuilder = new PGStateBuilder
  private val state0 = stateBuilder.newState().state
  private val state1 = stateBuilder.newState().state

  test("lookup uses the level-partitioned history before deferred indexing") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2)
    val states = (0L to FoundNodes.HISTORY_SIZE_BEFORE_INDEXING.toLong).map(id => nodeState(id, state0))

    states.foreach(state => promote(found, state, FORWARD))

    found.indexingRetiredFrontiers() shouldBe false
    found.retainedHistorySize() shouldBe FoundNodes.HISTORY_SIZE_BEFORE_INDEXING
    states.foreach(state => found.get(state.id(), state0.id()) should be theSameInstanceAs state)
    found.close()
  }

  test("activation freezes history and transfers the first retiring frontier") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2)
    fillFrozenHistory(found)
    val retiring = found.frontier(FORWARD).get(FoundNodes.HISTORY_SIZE_BEFORE_INDEXING)

    promote(found, nodeState(100, state0), FORWARD)

    found.indexingRetiredFrontiers() shouldBe true
    found.retainedHistorySize() shouldBe FoundNodes.HISTORY_SIZE_BEFORE_INDEXING
    found.retiredBucket(FoundNodes.HISTORY_SIZE_BEFORE_INDEXING) should be theSameInstanceAs retiring

    (101L to 104L).foreach(id => promote(found, nodeState(id, state0), FORWARD))
    found.retainedHistorySize() shouldBe FoundNodes.HISTORY_SIZE_BEFORE_INDEXING
    found.close()
  }

  test("a unique retired bucket is moved without losing NodeState identity") {
    val found = activatedFoundNodes()
    val unique = nodeState(500, state0)
    promote(found, unique, FORWARD)
    val activeBucket = found.frontier(FORWARD).get(unique.id())

    promote(found, nodeState(501, state0), FORWARD)

    found.retiredBucket(unique.id()) should be theSameInstanceAs activeBucket
    found.get(unique.id(), state0.id()) should be theSameInstanceAs unique
    found.close()
  }

  test("retirement merges disjoint state slots for a repeated graph node") {
    val found = activatedFoundNodes()
    val first = nodeState(500, state0)
    val second = nodeState(500, state1)

    promote(found, first, FORWARD)
    promote(found, nodeState(501, state0), FORWARD)
    val retainedBucket = found.retiredBucket(first.id())
    promote(found, second, FORWARD)
    promote(found, nodeState(502, state0), FORWARD)

    found.retiredBucket(first.id()) should be theSameInstanceAs retainedBucket
    found.get(first.id(), state0.id()) should be theSameInstanceAs first
    found.get(second.id(), state1.id()) should be theSameInstanceAs second
    found.close()
  }

  test("retirement accepts only the same canonical object in an occupied state slot") {
    val found = activatedFoundNodes()
    val canonical = nodeState(700, state0)
    promote(found, canonical, FORWARD)
    promote(found, nodeState(701, state0), FORWARD)

    promote(found, canonical, FORWARD)
    promote(found, nodeState(702, state0), FORWARD)
    val conflicting = nodeState(canonical.id(), state0)
    promote(found, conflicting, FORWARD)
    an[IllegalStateException] should be thrownBy promote(found, nodeState(703, state0), FORWARD)
    found.close()
  }

  test("bidirectional frontiers retire into one canonical lookup domain") {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Bidirectional, 2)
    fillFrozenHistory(found)
    promote(found, nodeState(100, state0), FORWARD)
    val backward = nodeState(900, state1)

    promote(found, backward, BACKWARD)
    promote(found, nodeState(901, state0), BACKWARD)

    found.get(backward.id(), state1.id()) should be theSameInstanceAs backward
    found.close()
  }

  test("empty frontier retirement neither grows history nor loses indexed state") {
    val found = activatedFoundNodes()
    val retained = nodeState(800, state0)
    promote(found, retained, FORWARD)
    promote(found, nodeState(801, state0), FORWARD)
    val frozenSize = found.retainedHistorySize()

    found.openBuffer()
    found.commitBuffer(FORWARD)
    found.openBuffer()
    found.commitBuffer(FORWARD)

    found.retainedHistorySize() shouldBe frozenSize
    found.get(retained.id(), state0.id()) should be theSameInstanceAs retained
    found.close()
  }

  test("close releases structural memory before and after activation") {
    val shallowTracker = new LocalMemoryTracker()
    val shallow = new FoundNodes(shallowTracker, SearchMode.Unidirectional, 2)
    promote(shallow, nodeState(1, state0), FORWARD)
    shallowTracker.estimatedHeapMemory() should be > 0L
    shallow.close()
    shallowTracker.estimatedHeapMemory() shouldBe 0L

    val deepTracker = new LocalMemoryTracker()
    val deep = new FoundNodes(deepTracker, SearchMode.Unidirectional, 2)
    fillFrozenHistory(deep)
    promote(deep, nodeState(100, state0), FORWARD)
    promote(deep, nodeState(101, state1), FORWARD)
    deep.get(FoundNodes.HISTORY_SIZE_BEFORE_INDEXING, state0.id()) should not be null
    deepTracker.estimatedHeapMemory() should be > 0L
    deep.close()
    deepTracker.estimatedHeapMemory() shouldBe 0L
  }

  private def activatedFoundNodes(): FoundNodes = {
    val found = new FoundNodes(new LocalMemoryTracker(), SearchMode.Unidirectional, 2)
    fillFrozenHistory(found)
    promote(found, nodeState(100, state0), FORWARD)
    found.indexingRetiredFrontiers() shouldBe true
    found
  }

  private def fillFrozenHistory(found: FoundNodes): Unit = {
    (0L to FoundNodes.HISTORY_SIZE_BEFORE_INDEXING.toLong).foreach(id => promote(found, nodeState(id, state0), FORWARD))
    found.retainedHistorySize() shouldBe FoundNodes.HISTORY_SIZE_BEFORE_INDEXING
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
