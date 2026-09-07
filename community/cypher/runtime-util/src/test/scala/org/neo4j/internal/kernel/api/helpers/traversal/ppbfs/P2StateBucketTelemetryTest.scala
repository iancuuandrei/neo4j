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
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap
import org.neo4j.cypher.internal.runtime.RuntimeUtilTestSuite
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.Lengths.trailMode
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.P2StateBucketTelemetry.LookupRole.BUFFER
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.P2StateBucketTelemetry.LookupRole.EXPANSION
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.P2StateBucketTelemetry.LookupRole.HISTORY
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.TraversalDirection.FORWARD
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.PGStateBuilder
import org.neo4j.kernel.api.StatementConstants.NO_SUCH_NODE
import org.neo4j.memory.EmptyMemoryTracker

import java.nio.file.Files

class P2StateBucketTelemetryTest extends RuntimeUtilTestSuite {

  private val stateBuilder = new PGStateBuilder
  private val states = (0 until 16).map(_ => stateBuilder.newState().state)

  test("concrete level buckets remain separate and operations are attributed to final occupancy") {
    val output = Files.createTempFile("ppbfs-p2-buckets", ".jsonl")
    try {
      val telemetry = new P2StateBucketTelemetry(output, 16, SearchMode.Unidirectional, "test-run")
      val first = bucket()
      val second = bucket()
      val firstLevel = level()
      val secondLevel = level()

      add(telemetry, first, 1, nodeState(11, 1))
      add(telemetry, first, 14, nodeState(11, 14))
      telemetry.recordWrite(first, 14)
      telemetry.recordLevelProbe(BUFFER, mapGetPerformed = true, bucketPresent = true)
      telemetry.recordBucketLookup(first, BUFFER, hit = true)
      firstLevel.put(11L, first)
      telemetry.recordBufferCommitted(firstLevel, FORWARD, 1, 1)
      telemetry.recordFullIteration(first)
      telemetry.recordBucketLookup(first, EXPANSION, hit = true)
      telemetry.recordFrontierRetired(firstLevel, FORWARD, 2, 2)

      add(telemetry, second, 1, nodeState(11, 1))
      secondLevel.put(11L, second)
      telemetry.recordBufferCommitted(secondLevel, FORWARD, 2, 2)
      telemetry.recordBucketLookup(second, HISTORY, hit = false)
      telemetry.recordFrontierRetired(secondLevel, FORWARD, 3, 3)
      telemetry.close()

      val json = Files.readString(output)
      json should include("\"runId\":\"test-run\"")
      json should include("\"createdBuckets\":2")
      json should include("\"committedBuckets\":2")
      json should include("\"uncommittedBuckets\":0")
      json should include("\"activeSlots\":3")
      json should include("\"denseSlots\":32")
      json should include("\"unusedDenseSlots\":29")
      json should include("\"occupancyHistogram\":{\"1\":1,\"2\":1}")
      json should include("\"runCountHistogram\":{\"1\":1,\"2\":1}")
      json should include("\"chunks8Histogram\":{\"1\":1,\"2\":1}")
      json should include("\"duplicateWritesByOccupancy\":{\"2\":1}")
      json should include("\"iterationsByOccupancy\":{\"2\":1}")
      json should include("\"iterationSlotsScanned\":16")
      json should include("\"iterationActiveStates\":2")
      json should include("\"iterationNullSlots\":14")
      json should include("\"EXPANSION\":{\"2\":1}")
      json should include("\"HISTORY\":{\"1\":1}")
      json should include("\"unknownBucketOperations\":0")
      json should include("\"globalDepthLifetimeHistogram\":{\"1\":2}")
      json should include("\"directionDepthLifetimeHistogram\":{\"1\":2}")
    } finally {
      Files.deleteIfExists(output)
    }
  }

  test("close finalizes a bucket that never reached a frontier") {
    val output = Files.createTempFile("ppbfs-p2-uncommitted", ".jsonl")
    try {
      val telemetry = new P2StateBucketTelemetry(output, 4, SearchMode.Unidirectional, "partial")
      val pending = HeapTrackingArrayList.newEmptyArrayList[NodeState](4, EmptyMemoryTracker.INSTANCE)

      telemetry.recordBucketCreated(pending, 0)
      telemetry.recordWrite(pending, 3)
      pending.set(3, nodeState(7, 3))
      telemetry.close()
      telemetry.close()

      val lines = Files.readAllLines(output)
      lines should have size 1
      lines.get(0) should include("\"createdBuckets\":1")
      lines.get(0) should include("\"committedBuckets\":0")
      lines.get(0) should include("\"uncommittedBuckets\":1")
      lines.get(0) should include("\"occupancyHistogram\":{\"1\":1}")
      lines.get(0) should include("\"openLevelCount\":0")
    } finally {
      Files.deleteIfExists(output)
    }
  }

  private def add(
    telemetry: P2StateBucketTelemetry,
    bucket: HeapTrackingArrayList[NodeState],
    stateId: Int,
    value: NodeState
  ): Unit = {
    if (bucket.forall(_ == null)) {
      telemetry.recordBucketCreated(bucket, 0)
    }
    telemetry.recordWrite(bucket, stateId)
    bucket.set(stateId, value)
  }

  private def bucket(): HeapTrackingArrayList[NodeState] =
    HeapTrackingArrayList.newEmptyArrayList[NodeState](16, EmptyMemoryTracker.INSTANCE)

  private def level(): HeapTrackingLongObjectHashMap[HeapTrackingArrayList[NodeState]] =
    HeapTrackingLongObjectHashMap.createLongObjectHashMap[HeapTrackingArrayList[NodeState]](
      EmptyMemoryTracker.INSTANCE
    )

  private def nodeState(nodeId: Long, stateId: Int): NodeState =
    new NodeState(globalState(), nodeId, states(stateId), NO_SUCH_NODE, trailMode())

  private def globalState(): GlobalState = {
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
