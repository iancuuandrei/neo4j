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
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs;

import static org.neo4j.memory.HeapEstimator.shallowSizeOfInstance;

import org.neo4j.collection.trackable.HeapTrackingArrayList;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.util.Preconditions;

/**
 * The canonical states retained for one data-graph node.
 *
 * <p>Most nodes have only one or two occupied product-graph states. Those
 * states are kept in fields so that the common case does not allocate an
 * array per node. A bucket promotes once, before the first insertion which
 * exceeds its configured sparse capacity, to the existing heap-tracked dense
 * state-id indexed representation. Promotion is deliberately complete before
 * the dense list is published, preserving canonical identity if allocation or
 * copying fails.
 */
final class StateBucket {
    private static final long SHALLOW_SIZE = shallowSizeOfInstance(StateBucket.class);

    private final MemoryTracker memoryTracker;
    private final int sparseCapacity;
    private final int nfaStateCount;

    private int firstStateId = -1;
    private int secondStateId = -1;
    private NodeState firstState;
    private NodeState secondState;
    private HeapTrackingArrayList<NodeState> denseStates;

    StateBucket(MemoryTracker memoryTracker, int nfaStateCount, int sparseCapacity) {
        Preconditions.checkArgument(
                sparseCapacity == 1 || sparseCapacity == 2,
                "Sparse state bucket capacity must be 1 or 2, was %d",
                sparseCapacity);
        Preconditions.checkArgument(nfaStateCount > 0, "NFA state count must be positive, was %d", nfaStateCount);
        this.memoryTracker = memoryTracker;
        this.nfaStateCount = nfaStateCount;
        this.sparseCapacity = sparseCapacity;
        memoryTracker.allocateHeap(SHALLOW_SIZE);
    }

    NodeState get(int stateId) {
        if (denseStates != null) {
            return denseStates.get(stateId);
        }
        if (firstStateId == stateId) {
            return firstState;
        }
        if (secondStateId == stateId) {
            return secondState;
        }
        return null;
    }

    void put(int stateId, NodeState nodeState) {
        var existing = get(stateId);
        Preconditions.checkState(
                existing == null || existing == nodeState, "Attempted to replace canonical NodeState instance");
        if (existing != null) {
            return;
        }

        if (denseStates != null) {
            denseStates.set(stateId, nodeState);
        } else if (firstStateId == -1) {
            firstStateId = stateId;
            firstState = nodeState;
        } else if (sparseCapacity == 2 && secondStateId == -1) {
            secondStateId = stateId;
            secondState = nodeState;
        } else {
            promote();
            denseStates.set(stateId, nodeState);
        }
    }

    private void promote() {
        // Build and populate the replacement privately. The field is assigned
        // only after every prior canonical identity has been copied.
        var promoted = HeapTrackingArrayList.<NodeState>newEmptyArrayList(nfaStateCount, memoryTracker);
        promoted.set(firstStateId, firstState);
        if (secondStateId != -1) {
            promoted.set(secondStateId, secondState);
        }
        denseStates = promoted;
        firstState = null;
        secondState = null;
        firstStateId = -1;
        secondStateId = -1;
    }
}
