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

import org.neo4j.collection.trackable.HeapTrackingArrayList;
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
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
 * The canonical repository and frontier scheduling have separate lifecycles. {@code allStates} retains exactly one
 * reference for every discovered product-state key for the whole search and provides direct lookup. The forward and
 * backward frontiers contain only states eligible for the next expansion in that direction. {@code frontierBuffer}
 * collects the next frontier while the current frontier is being iterated.
 *
 * <p>All collections use the same two-level representation: a node-id map whose values are dense arrays indexed by
 * sequential NFA state id. A state is registered in the canonical repository before its buffer entry becomes visible,
 * so recursive juxtaposition processing can resolve the same instance. Retiring a frontier releases only its
 * scheduling structures; canonical lookup ownership lasts until this repository is closed.
 *
 * <p>Bidirectional search has distinct forward and backward frontiers but shares one buffer because only one direction
 * expands at a time. Both directions share the canonical product-state repository.
 */
public final class FoundNodes implements AutoCloseable {
    private final HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>>
            allStates; // nodeId x stateId -> NodeState

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

    private int forwardDepth = 0;

    private int backwardDepth = 0;

    public FoundNodes(MemoryTracker memoryTracker, SearchMode mode, int nfaStateCount, PPBFSHooks hooks) {
        this.memoryTracker = memoryTracker.getScopedMemoryTracker();
        this.mode = mode;
        this.hooks = hooks;
        this.allStates = HeapTrackingLongObjectHashMap.createLongObjectHashMap(this.memoryTracker);
        this.forwardFrontier = HeapTrackingLongObjectHashMap.createLongObjectHashMap(this.memoryTracker);
        if (mode == SearchMode.Bidirectional) {
            this.backwardFrontier = HeapTrackingLongObjectHashMap.createLongObjectHashMap(this.memoryTracker);
        }
        this.frontierBuffer = HeapTrackingLongObjectHashMap.createLongObjectHashMap(this.memoryTracker);
        this.nfaStateCount = nfaStateCount;
    }

    public void addToBuffer(NodeState nodeState) {
        Preconditions.checkState(bufferState == BufferState.OPEN, "NodeState added to closed buffer");
        var allStatesForNode = allStates.get(nodeState.id());
        if (allStatesForNode == null) {
            allStatesForNode = HeapTrackingArrayList.newEmptyArrayList(nfaStateCount, memoryTracker);
            allStates.put(nodeState.id(), allStatesForNode);
        }
        var existing = allStatesForNode.get(nodeState.state().id());
        Preconditions.checkState(
                existing == null || existing == nodeState, "Attempted to replace canonical NodeState instance");
        allStatesForNode.set(nodeState.state().id(), nodeState);

        var nodeStates = frontierBuffer.get(nodeState.id());
        var newNodeBucket = nodeStates == null;
        if (nodeStates == null) {
            nodeStates = HeapTrackingArrayList.newEmptyArrayList(nfaStateCount, memoryTracker);
            frontierBuffer.put(nodeState.id(), nodeStates);
        }
        nodeStates.set(nodeState.state().id(), nodeState);
        hooks.foundNodesBufferAdd(newNodeBucket, nfaStateCount);
    }

    /** Look up a NodeState by its canonical product-state key. */
    public NodeState get(long nodeId, int stateId) {
        var nodeState = getFromLevel(allStates, nodeId, stateId);
        if (nodeState != null) {
            hooks.foundNodesLookup(LookupLocation.DIRECT, 0, -1, totalDepth());
            return nodeState;
        }
        hooks.foundNodesLookup(LookupLocation.MISS, 0, -1, totalDepth());
        return null;
    }

    private NodeState getFromLevel(
            HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>> level, long nodeId, int stateId) {
        if (level.isEmpty()) {
            return null;
        }
        var nodeStates = level.get(nodeId);
        if (nodeStates == null) {
            return null;
        }
        return nodeStates.get(stateId);
    }

    /** Allocates a new buffer based on the size of the previous one */
    public void openBuffer() {
        Preconditions.checkState(bufferState == BufferState.CLOSED, "Buffer opened when it was not closed");
        frontierBuffer = HeapTrackingLongObjectHashMap.createLongObjectHashMap(
                this.memoryTracker, Math.max(1, frontierBuffer.size()));
        bufferState = BufferState.OPEN;
    }

    /** Retires the previous frontier and promotes the frontier buffer. */
    public void commitBuffer(TraversalDirection direction) {
        Preconditions.checkState(bufferState == BufferState.OPEN, "Buffer closed when it was not open");

        switch (direction) {
            case FORWARD -> {
                closeLevel(forwardFrontier);
                forwardDepth += 1;
                forwardFrontier = frontierBuffer;
            }
            case BACKWARD -> {
                closeLevel(backwardFrontier);
                backwardDepth += 1;
                backwardFrontier = frontierBuffer;
            }
        }
        bufferState = BufferState.CLOSED;
    }

    private static void closeLevel(HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>> level) {
        level.forEachValue(HeapTrackingArrayList::close);
        level.close();
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
        DIRECT,
        MISS
    }
}
