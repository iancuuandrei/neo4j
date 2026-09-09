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

import org.neo4j.collection.trackable.HeapTrackingCollections;
import org.neo4j.collection.trackable.HeapTrackingIntHashSet;
import org.neo4j.collection.trackable.HeapTrackingUnifiedMap;
import org.neo4j.memory.MemoryTracker;

/**
 * P7: post-saturation memoized completion for WALK-mode path tracing.
 *
 * <p>After a target saturates (no more result paths needed), {@link PathTracer} must still exhaust
 * the DFS so that PPBFS bookkeeping side effects ({@code minTargetDistance} / target-signpost
 * registration and its propagation scheduling) are established exactly as exhaustive tracing would
 * establish them. In WALK mode every remaining side effect is a pure function of the compact state
 * {@code (NodeState, sourceLength)}: WALK performs no relationship/node uniqueness tracking, its
 * lengths need no TRAIL/ACYCLIC-style validation, and tracing adds no new seen lengths. Two traces
 * reaching the same {@code (NodeState, sourceLength)} therefore have identical bookkeeping
 * suffixes, so the suffix is computed once and revisited subtrees are skipped.
 *
 * <p>This reduces post-saturation tracing work from {@code |T|} (represented path combinations,
 * potentially exponential, e.g. {@code O(2^d)} for repeated diamonds) to work proportional to the
 * number of distinct compact states plus the number of eligible {@code (signpost, sourceLength)}
 * pushes. Every signpost is still pushed (and first-traced, preserving first-discovery order and
 * {@code minTargetDistance} values) exactly once per eligible source length; only the
 * <em>descent</em> into an already-completed state is skipped.
 *
 * <p>Scope is intentionally narrow (smallest safe mechanism on the Pareto frontier):
 * <ul>
 *   <li>WALK mode only. TRAIL validity depends on {@code (NodeState, sourceLength,
 *   usedRelationships)} and ACYCLIC on {@code (NodeState, sourceLength, usedNodes)}; those
 *   histories are exponential, so history-sensitive validation/pruning keeps the existing
 *   exhaustive tracing in those modes.</li>
 *   <li>Allocated lazily, only when a target actually saturates while the tracer keeps running
 *   (in practice: unbound/multi-target searches; bound {@code intoTarget} searches terminate
 *   globally on saturation and never re-enter the tracer, so they pay no P7 tax).</li>
 *   <li>Continues from the suspended DFS state (existing {@link SignpostStack} cursors are never
 *   rewound), preserving current first-discovery and scheduling order.</li>
 * </ul>
 *
 * <p>Lifecycle: one instance per target-tracing epoch. {@link PathTracer#reset()} must close it
 * (releasing memory-tracker accounting) because {@code sourceLength} keys are relative to the
 * epoch's target depth and incomparable across epochs.
 */
final class PostSaturationMemo implements AutoCloseable {
    /** States whose bookkeeping suffix has been fully computed since saturation. */
    private final HeapTrackingUnifiedMap<NodeState, HeapTrackingIntHashSet> completed;
    /** States on the current DFS path (for zero-length NodeSignpost cycle re-entry). */
    private final HeapTrackingUnifiedMap<NodeState, HeapTrackingIntHashSet> onPath;

    private final MemoryTracker memoryTracker;
    /** Whether {@link #onPath} has been seeded with the suspended DFS path at engagement. */
    private boolean seeded;

    PostSaturationMemo(MemoryTracker memoryTracker) {
        this.memoryTracker = memoryTracker;
        this.completed = HeapTrackingCollections.newMap(memoryTracker);
        this.onPath = HeapTrackingCollections.newMap(memoryTracker);
    }

    /**
     * Seed {@link #onPath} with the states on the suspended DFS path, from the target down to the
     * current head. Runs once, the first time the memo is consulted after saturation, so that a
     * zero-length cycle re-entry is detected even if the cycle was entered before saturation
     * (pre-saturation levels are not tracked to keep the common-case tax at zero).
     */
    void ensureSeeded(SignpostStack stack) {
        if (seeded) {
            return;
        }
        seeded = true;
        int lengthToTarget = 0;
        addToSet(onPath, stack.node(0), stack.dgLength());
        for (int i = 0; i < stack.size(); i++) {
            lengthToTarget += stack.signpost(i).dataGraphLength();
            addToSet(onPath, stack.node(i + 1), stack.dgLength() - lengthToTarget);
        }
    }

    /** True if this state's bookkeeping suffix was already fully computed post-saturation. */
    boolean isCompleted(NodeState node, int sourceLength) {
        var set = completed.get(node);
        return set != null && set.contains(sourceLength);
    }

    /** True if this state is already on the current DFS path (zero-length cycle re-entry). */
    boolean isOnPath(NodeState node, int sourceLength) {
        var set = onPath.get(node);
        return set != null && set.contains(sourceLength);
    }

    void onDescend(NodeState node, int sourceLength) {
        addToSet(onPath, node, sourceLength);
    }

    /** Mark a fully-iterated level complete and remove it from the current path. */
    void onLevelExhausted(NodeState node, int sourceLength) {
        addToSet(completed, node, sourceLength);
        var set = onPath.get(node);
        if (set != null) {
            set.remove(sourceLength);
        }
    }

    private void addToSet(HeapTrackingUnifiedMap<NodeState, HeapTrackingIntHashSet> map, NodeState node, int length) {
        var set = map.get(node);
        if (set == null) {
            set = HeapTrackingCollections.newIntSet(memoryTracker);
            map.put(node, set);
        }
        set.add(length);
    }

    @Override
    public void close() {
        for (var sets : completed.values()) {
            sets.close();
        }
        completed.close();
        for (var sets : onPath.values()) {
            sets.close();
        }
        onPath.close();
    }
}
