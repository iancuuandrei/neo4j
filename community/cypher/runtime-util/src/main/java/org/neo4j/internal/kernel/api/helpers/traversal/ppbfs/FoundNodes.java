/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Neo4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Neo4j.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs;

import org.neo4j.collection.trackable.HeapTrackingArrayList;
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.P2StateBucketTelemetry.LookupRole;
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.util.Preconditions;

/**
 * This class holds all of the (node,state) pairs that are found during PGPathPropagatingBFS evaluation.
 * <p>
 * It is organised first by datagraph node id, and then by NFA state id, so that all of the states for a node can be
 * retrieved and supplied to the product graph cursor at once.
 *
 *
 * <pre>
 * We store all the nodes we've seen level by level. We do this so that we can have access to both the current and
 * next levels, without duplicating data, or reallocating collections due to .grow calls.
 *
 * To enable us to group nodes by their data graph id, we keep NodeStates in something similar to a two dimensional
 * hash map. For example, to get the node (nodeId=2, stateId=3) from the currentLevel, we'd call
 * currentLevel.get(2).get(3). The fact that stateId's are sequential allows us to let the type of currentLevel
 * be HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>>, so currentLevel.get(2) returns an array list,
 * where the NodeState corresponding to stateId=3 is stored at index 3. This may lead to over allocation and sparse
 * arrays for certain NFA's, so we may want to revise this in the future if benchmarks tell us to.
 *
 * We keep all of our nodes in three disjoint collections, all of them adhere to the same indexing scheme as
 * explained above for currentLevel:
 *
 *  1) history. This is an array list of (nodeId, stateId) -> nodeState maps which store all the levels we've
 *     previously seen. So previousLevels.get(3) stores the nodes which were discovered in the previous level
 *  2) frontier. This is a map with (nodeId, stateId) -> nodeState for the current level
 *  3) frontierBuffer. This is a map with (nodeId, stateId) -> nodeState for the next level, so that we can iterate
 *     over the current frontier while collecting new nodes for the next frontier
 *
 *  So for example, if we're currently expanding level 3, to find nodes at distance 4 from the source,
 *  our data would look like
 *
 *  ┌───────────────────────────┐
 *  │          history          │
 *  │ ┌─────┐  ┌─────┐  ┌─────┐ │  ┌──────────────┐  ┌────────────┐
 *  │ │  0  │  │  1  │  │  2  │ │  │ 3 (frontier) │  │ 4 (buffer) │
 *  │ └─────┘  └─────┘  └─────┘ │  └──────────────┘  └────────────┘
 *  └───────────────────────────┘
 *
 * Keeping our nodeStates batched by level like this allows us to avoid rehashing and reallocating the whole
 * collection when we need to grow it, we only ever rehash/reallocate the buffer as it grows.
 *
 * A downside of this design is that looking up a node is linear w.r.t the depth of the bfs.
 *
 * We also support a bidirectional mode, which allocates two frontiers: one for forwards traversal and one
 * for backwards traversal. They share the same buffer since we only expand in one direction at a time.
 * </pre>
 */
public final class FoundNodes implements AutoCloseable {
    private final HeapTrackingArrayList<HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>>>
            history; // levelDepth x nodeId x stateId -> NodeState

    private HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>>
            forwardFrontier; // nodeId x stateId -> NodeState
    private HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>>
            backwardFrontier; // nodeId x stateId -> NodeState

    private BufferState bufferState = BufferState.CLOSED;
    private HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>>
            frontierBuffer; // nodeId x stateId -> NodeState

    private final MemoryTracker memoryTracker;
    private final PPBFSHooks hooks;
    private final SearchMode mode;
    private final int nfaStateCount;
    private final P2StateBucketTelemetry bucketTelemetry;

    private int forwardDepth = 0;

    private int backwardDepth = 0;

    public FoundNodes(MemoryTracker memoryTracker, SearchMode mode, int nfaStateCount, PPBFSHooks hooks) {
        this.memoryTracker = memoryTracker.getScopedMemoryTracker();
        this.mode = mode;
        this.hooks = hooks;
        this.history = HeapTrackingArrayList.newArrayList(this.memoryTracker);
        this.forwardFrontier = HeapTrackingLongObjectHashMap.createLongObjectHashMap(this.memoryTracker);
        if (mode == SearchMode.Bidirectional) {
            this.backwardFrontier = HeapTrackingLongObjectHashMap.createLongObjectHashMap(this.memoryTracker);
        }
        this.frontierBuffer = HeapTrackingLongObjectHashMap.createLongObjectHashMap(this.memoryTracker);
        this.nfaStateCount = nfaStateCount;
        this.bucketTelemetry = P2StateBucketTelemetry.configured(nfaStateCount, mode);
    }

    public void addToBuffer(NodeState nodeState) {
        Preconditions.checkState(bufferState == BufferState.OPEN, "NodeState added to closed buffer");
        var nodeStates = frontierBuffer.get(nodeState.id());
        var newNodeBucket = nodeStates == null;
        if (nodeStates == null) {
            nodeStates = HeapTrackingArrayList.newEmptyArrayList(nfaStateCount, memoryTracker);
            frontierBuffer.put(nodeState.id(), nodeStates);
            bucketTelemetry.recordBucketCreated(nodeStates, totalDepth());
        }
        bucketTelemetry.recordWrite(nodeStates, nodeState.state().id());
        nodeStates.set(nodeState.state().id(), nodeState);
        hooks.foundNodesBufferAdd(newNodeBucket, nfaStateCount);
        hooks.foundNodesState(nodeState.id(), nodeState.state().id(), nfaStateCount);
    }

    /** Look up a NodeState. O(N) wrt history length */
    public NodeState get(long nodeId, int stateId) {
        var nodeState = getFromLevel(frontierBuffer, nodeId, stateId, LookupRole.BUFFER);
        if (nodeState != null) {
            hooks.foundNodesLookup(LookupLocation.BUFFER, 0, -1, history.size());
            return nodeState;
        }

        nodeState = getFromLevel(forwardFrontier, nodeId, stateId, LookupRole.FORWARD_FRONTIER);
        if (nodeState != null) {
            hooks.foundNodesLookup(LookupLocation.FORWARD_FRONTIER, 0, -1, history.size());
            return nodeState;
        }

        if (mode == SearchMode.Bidirectional) {
            nodeState = getFromLevel(backwardFrontier, nodeId, stateId, LookupRole.BACKWARD_FRONTIER);
            if (nodeState != null) {
                hooks.foundNodesLookup(LookupLocation.BACKWARD_FRONTIER, 0, -1, history.size());
                return nodeState;
            }
        }

        for (int i = history.size() - 1; i >= 0; i--) {
            nodeState = getFromLevel(history.get(i), nodeId, stateId, LookupRole.HISTORY);
            if (nodeState != null) {
                var historyHitAge = history.size() - i - 1;
                hooks.foundNodesLookup(LookupLocation.HISTORY, historyHitAge + 1, historyHitAge, history.size());
                return nodeState;
            }
        }
        hooks.foundNodesLookup(LookupLocation.MISS, history.size(), -1, history.size());
        return null;
    }

    private NodeState getFromLevel(
            HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>> level,
            long nodeId,
            int stateId,
            LookupRole role) {
        if (level.isEmpty()) {
            bucketTelemetry.recordMapProbe(role, false);
            return null;
        }
        var nodeStates = level.get(nodeId);
        bucketTelemetry.recordMapProbe(role, nodeStates != null);
        if (nodeStates == null) {
            return null;
        }
        var nodeState = nodeStates.get(stateId);
        bucketTelemetry.recordBucketLookup(nodeStates, role, nodeState != null);
        return nodeState;
    }

    void recordFrontierIteration(HeapTrackingArrayList<NodeState> nodeStates) {
        bucketTelemetry.recordFullIteration(nodeStates);
    }

    NodeState getFromExpansionBucket(HeapTrackingArrayList<NodeState> nodeStates, int stateId) {
        var nodeState = nodeStates.get(stateId);
        bucketTelemetry.recordBucketLookup(nodeStates, LookupRole.EXPANSION, nodeState != null);
        return nodeState;
    }

    /** Allocates a new buffer based on the size of the previous one */
    public void openBuffer() {
        Preconditions.checkState(bufferState == BufferState.CLOSED, "Buffer opened when it was not closed");
        frontierBuffer = HeapTrackingLongObjectHashMap.createLongObjectHashMap(
                this.memoryTracker, Math.max(1, frontierBuffer.size()));
        bufferState = BufferState.OPEN;
    }

    /** Shifts the previous frontier into history, and the frontier buffer into the current frontier */
    public void commitBuffer(TraversalDirection direction) {
        Preconditions.checkState(bufferState == BufferState.OPEN, "Buffer closed when it was not open");

        switch (direction) {
            case FORWARD -> {
                var retiringFrontier = forwardFrontier;
                if (retiringFrontier.notEmpty()) {
                    history.add(retiringFrontier);
                }
                forwardDepth += 1;
                bucketTelemetry.recordFrontierRetired(retiringFrontier, totalDepth(), forwardDepth);
                forwardFrontier = frontierBuffer;
                bucketTelemetry.recordBufferCommitted(forwardFrontier, direction, totalDepth(), forwardDepth);
            }
            case BACKWARD -> {
                var retiringFrontier = backwardFrontier;
                if (retiringFrontier.notEmpty()) {
                    history.add(retiringFrontier);
                }
                backwardDepth += 1;
                bucketTelemetry.recordFrontierRetired(retiringFrontier, totalDepth(), backwardDepth);
                backwardFrontier = frontierBuffer;
                bucketTelemetry.recordBufferCommitted(backwardFrontier, direction, totalDepth(), backwardDepth);
            }
        }
        bufferState = BufferState.CLOSED;
    }

    public HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>> frontier(TraversalDirection direction) {
        return switch (direction) {
            case FORWARD -> forwardFrontier;
            case BACKWARD -> backwardFrontier;
        };
    }

    public TraversalDirection getNextExpansionDirection() {
        if (mode == SearchMode.Unidirectional) {
            return TraversalDirection.FORWARD;
        }

        if (forwardFrontier.isEmpty()) {
            return TraversalDirection.BACKWARD;
        }
        if (backwardFrontier.isEmpty()) {
            return TraversalDirection.FORWARD;
        }

        if (backwardFrontier.size() < forwardFrontier.size()) {
            return TraversalDirection.BACKWARD;
        }
        return TraversalDirection.FORWARD;
    }

    public boolean hasMore() {
        Preconditions.checkState(bufferState == BufferState.CLOSED, "Should not check frontier state when buffer open");

        if (mode == SearchMode.Unidirectional) {
            return forwardFrontier.notEmpty();
        }
        return forwardFrontier.notEmpty() && backwardFrontier.notEmpty();
    }

    @Override
    public void close() {
        bucketTelemetry.close();
        // we don't need to iterate & close the inner collections because we can just close the scoped memory tracker
        this.memoryTracker.close();
    }

    public int forwardDepth() {
        return forwardDepth;
    }

    public int backwardDepth() {
        return backwardDepth;
    }

    public int totalDepth() {
        return forwardDepth + backwardDepth;
    }

    public int depth(TraversalDirection direction) {
        return switch (direction) {
            case FORWARD -> forwardDepth;
            case BACKWARD -> backwardDepth;
        };
    }

    private enum BufferState {
        OPEN,
        CLOSED
    }

    public enum LookupLocation {
        BUFFER,
        FORWARD_FRONTIER,
        BACKWARD_FRONTIER,
        HISTORY,
        MISS
    }
}
