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

import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;
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
 * Frontier nodes are stored by level for traversal. A separate canonical repository provides direct lookup across
 * all levels.
 *
 * To enable us to group nodes by their data graph id, we keep NodeStates in something similar to a two dimensional
 * hash map. For example, to get the node (nodeId=2, stateId=3) from the currentLevel, we'd call
 * currentLevel.get(2).get(3). The type of currentLevel is
 * HeapTrackingLongObjectHashMap<StateBucket>, so currentLevel.get(2) returns the bucket holding only the
 * active states for that node in ascending state-id order, which avoids allocating and scanning one slot
 * per NFA state for every visited node.
 *
 * We keep active nodes in two frontier collections using the indexing scheme above:
 *
 *  1) frontier. This is a map with (nodeId, stateId) -> nodeState for the current level
 *  2) frontierBuffer. This is a map with (nodeId, stateId) -> nodeState for the next level, so that we can iterate
 *     over the current frontier while collecting new nodes for the next frontier
 *
 * Retired frontiers are released. The canonical repository keeps one reference to each NodeState and makes lookup
 * independent of BFS depth.
 *
 * We also support a bidirectional mode, which allocates two frontiers: one for forwards traversal and one
 * for backwards traversal. They share the same buffer since we only expand in one direction at a time.
 * </pre>
 */
public final class FoundNodes implements AutoCloseable {
    private final HeapTrackingLongObjectHashMap<StateBucket> allStates; // nodeId x stateId -> NodeState

    private HeapTrackingLongObjectHashMap<StateBucket> forwardFrontier; // nodeId x stateId -> NodeState
    private HeapTrackingLongObjectHashMap<StateBucket> backwardFrontier; // nodeId x stateId -> NodeState

    private BufferState bufferState = BufferState.CLOSED;
    private HeapTrackingLongObjectHashMap<StateBucket> frontierBuffer; // nodeId x stateId -> NodeState

    private final MemoryTracker memoryTracker;
    private final SearchMode mode;
    private final int nfaStateCount;

    private int forwardDepth = 0;

    private int backwardDepth = 0;

    public FoundNodes(MemoryTracker memoryTracker, SearchMode mode, int nfaStateCount) {
        this.memoryTracker = memoryTracker.getScopedMemoryTracker();
        this.mode = mode;
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
            allStatesForNode = new StateBucket(memoryTracker);
            allStates.put(nodeState.id(), allStatesForNode);
        }
        // StateBucket.put enforces the canonical-identity invariant internally.
        allStatesForNode.put(nodeState);

        var nodeStates = frontierBuffer.get(nodeState.id());
        if (nodeStates == null) {
            nodeStates = new StateBucket(memoryTracker);
            frontierBuffer.put(nodeState.id(), nodeStates);
        }
        nodeStates.put(nodeState);
    }

    /** Look up a NodeState by its canonical product-state key. */
    public NodeState get(long nodeId, int stateId) {
        return getFromLevel(allStates, nodeId, stateId);
    }

    private NodeState getFromLevel(HeapTrackingLongObjectHashMap<StateBucket> level, long nodeId, int stateId) {
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

    private static void closeLevel(HeapTrackingLongObjectHashMap<StateBucket> level) {
        level.forEachValue(StateBucket::close);
        level.close();
    }

    public HeapTrackingLongObjectHashMap<StateBucket> frontier(TraversalDirection direction) {
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
}
