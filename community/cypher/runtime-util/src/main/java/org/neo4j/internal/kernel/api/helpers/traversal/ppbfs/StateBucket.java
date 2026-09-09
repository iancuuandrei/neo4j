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
import static org.neo4j.memory.HeapEstimator.shallowSizeOfObjectArray;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.neo4j.collection.trackable.HeapTrackingArrayList;
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.State;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.util.Preconditions;

/**
 * The set of {@link NodeState}s known for one data-graph node within one BFS level.
 *
 * <p>Most level-owned buckets hold far fewer states than the NFA has in total, so this stores only
 * the active states, sorted by {@code state().id()}. Ascending state-id iteration order matches the
 * previous dense array representation. Exact lookup is a linear scan with early exit, which is
 * cheaper than direct indexing while the bucket stays small; there is deliberately no promotion to
 * a dense array (see the P2 research archive for the crossover analysis).
 *
 * <p>Insertion is O(1) when state ids arrive in ascending order (the common forward-traversal case)
 * and O(k) with a single backing-array shift otherwise. Re-inserting the known instance for an id
 * is a no-op; inserting a different instance for a known id fails, preserving the canonical
 * {@link NodeState} identity invariant relied upon by {@link FoundNodes}.
 *
 * <p>This class is public only because the {@code ppbfs.hooks} debug-logging package iterates
 * frontier buckets; it is not a general-purpose collection and must not be used outside PPBFS.
 */
public final class StateBucket implements AutoCloseable, Iterable<NodeState> {
    private static final long SHALLOW_SIZE = shallowSizeOfInstance(StateBucket.class);
    private static final int INITIAL_CAPACITY = 4;

    private final MemoryTracker memoryTracker;
    private NodeState[] states;
    private int size;

    StateBucket(MemoryTracker memoryTracker) {
        this.memoryTracker = memoryTracker;
        this.states = new NodeState[INITIAL_CAPACITY];
        memoryTracker.allocateHeap(SHALLOW_SIZE + shallowSizeOfObjectArray(INITIAL_CAPACITY));
    }

    NodeState get(int stateId) {
        var states = this.states;
        for (int i = 0; i < size; i++) {
            var candidate = states[i].state().id();
            if (candidate == stateId) {
                return states[i];
            }
            if (candidate > stateId) {
                return null;
            }
        }
        return null;
    }

    void put(NodeState nodeState) {
        int stateId = nodeState.state().id();
        if (size == 0) {
            states[size++] = nodeState;
            return;
        }
        int lastId = states[size - 1].state().id();
        if (stateId > lastId) {
            ensureCapacity(size + 1);
            states[size++] = nodeState;
            return;
        }
        if (stateId == lastId) {
            Preconditions.checkState(
                    states[size - 1] == nodeState, "Attempted to replace canonical NodeState instance");
            return;
        }
        int i = 0;
        while (states[i].state().id() < stateId) {
            i++;
        }
        if (states[i].state().id() == stateId) {
            Preconditions.checkState(states[i] == nodeState, "Attempted to replace canonical NodeState instance");
            return;
        }
        ensureCapacity(size + 1);
        System.arraycopy(states, i, states, i + 1, size - i);
        states[i] = nodeState;
        size++;
    }

    int activeSize() {
        return size;
    }

    void appendActiveStatesTo(HeapTrackingArrayList<State> target) {
        for (int i = 0; i < size; i++) {
            target.add(states[i].state());
        }
    }

    @Override
    public Iterator<NodeState> iterator() {
        return new Iterator<>() {
            private int cursor;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public NodeState next() {
                if (cursor >= size) {
                    throw new NoSuchElementException();
                }
                return states[cursor++];
            }
        };
    }

    @Override
    public void close() {
        memoryTracker.releaseHeap(SHALLOW_SIZE + shallowSizeOfObjectArray(states.length));
    }

    private void ensureCapacity(int required) {
        if (required > states.length) {
            int grown = Math.max(required, states.length * 2);
            var replacement = new NodeState[grown];
            System.arraycopy(states, 0, replacement, 0, size);
            memoryTracker.allocateHeap(shallowSizeOfObjectArray(grown));
            memoryTracker.releaseHeap(shallowSizeOfObjectArray(states.length));
            states = replacement;
        }
    }
}
