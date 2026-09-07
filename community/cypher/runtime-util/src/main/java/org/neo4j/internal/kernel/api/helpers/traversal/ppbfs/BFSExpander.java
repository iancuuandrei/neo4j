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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import org.neo4j.collection.trackable.HeapTracking;
import org.neo4j.collection.trackable.HeapTrackingArrayList;
import org.neo4j.collection.trackable.HeapTrackingCollections;
import org.neo4j.collection.trackable.HeapTrackingUnifiedMap;
import org.neo4j.function.Predicates;
import org.neo4j.graphdb.Direction;
import org.neo4j.internal.helpers.collection.PrefetchingIterator;
import org.neo4j.internal.kernel.api.KernelReadTracer;
import org.neo4j.internal.kernel.api.RelationshipTraversalEntities;
import org.neo4j.internal.kernel.api.helpers.traversal.ReversedRelTraversalEntities;
import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks.PPBFSHooks;
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.ProductGraphTraversalCursor;
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.RelationshipPredicate;
import org.neo4j.internal.kernel.api.helpers.traversal.productgraph.State;
import org.neo4j.memory.HeapEstimator;
import org.neo4j.memory.MemoryTracker;
import org.neo4j.storageengine.api.RelationshipSelection;

final class BFSExpander implements AutoCloseable {
    private final MemoryTracker mt;
    private final PPBFSHooks hooks;
    private final GlobalState globalState;
    private final ProductGraphTraversalCursor pgCursor;
    private final CachingRelCursor relCursor;
    private final long intoTarget;
    private final TraversalPathModeFactory tracker;

    // allocated once and reused per source nodeState
    private final HeapTrackingArrayList<State> statesList;
    private final FoundNodes foundNodes;

    record CachedRelPredicate(Predicate<RelationshipTraversalEntities> predicate, long rel, TraversalDirection dir) {
        public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(CachedRelPredicate.class);
    }

    record CachedNodePredicate(LongPredicate predicate, long node) {
        public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(CachedNodePredicate.class);
    }

    private final HeapTrackingUnifiedMap<CachedRelPredicate, Boolean> relPredicateCache;
    private final HeapTrackingUnifiedMap<CachedNodePredicate, Boolean> nodePredicateCache;

    public BFSExpander(
            FoundNodes foundNodes,
            GlobalState globalState,
            ProductGraphTraversalCursor pgCursor,
            ProductGraphTraversalCursor.DataGraphRelationshipCursor relCursor,
            long intoTarget,
            int nfaStateCount,
            TraversalPathModeFactory tracker) {
        this.mt = globalState.mt;
        this.hooks = globalState.hooks;
        this.globalState = globalState;
        this.pgCursor = pgCursor;
        this.relCursor = new CachingRelCursor(relCursor, mt);
        this.intoTarget = intoTarget;
        this.statesList = HeapTrackingArrayList.newArrayList(nfaStateCount, mt);
        this.foundNodes = foundNodes;
        this.tracker = tracker;
        this.relPredicateCache = HeapTrackingCollections.newMap(mt);
        this.nodePredicateCache = HeapTrackingCollections.newMap(mt);
    }

    /** discover a nodeState that has not been seen before */
    public void discover(NodeState node, TraversalDirection direction) {
        hooks.discover(node, direction);
        foundNodes.addToBuffer(node);
        node.discover(direction);

        var state = node.state();

        for (var nj : state.getNodeJuxtapositions(direction)) {
            if (nj.endState(direction).test(node.id())) {
                switch (direction) {
                    case FORWARD -> {
                        var nextNode = encounter(node.id(), nj.targetState(), direction);
                        var signpost = TwoWaySignpost.fromNodeJuxtaposition(
                                mt, node, nextNode, foundNodes.forwardDepth(), tracker.lengths());
                        if (globalState.searchMode == SearchMode.Unidirectional
                                || !nextNode.hasSourceSignpost(signpost)) {
                            nextNode.addSourceSignpost(signpost, foundNodes.forwardDepth());
                        }
                    }

                    case BACKWARD -> {
                        var nextNode = encounter(node.id(), nj.sourceState(), direction);
                        var signpost = TwoWaySignpost.fromNodeJuxtaposition(mt, nextNode, node, tracker.lengths());

                        if (!nextNode.hasTargetSignpost(signpost)) {
                            var addedSignpost = node.upsertSourceSignpost(signpost);
                            addedSignpost.setMinTargetDistance(
                                    foundNodes.backwardDepth(), PGPathPropagatingBFS.Phase.Expansion);
                        }
                    }
                }
            }
        }
    }

    /** encounter a nodeState that may or may not have been seen before */
    public NodeState encounter(long nodeId, State state, TraversalDirection direction) {
        var nodeState = foundNodes.get(nodeId, state.id());

        if (nodeState == null) {
            nodeState = new NodeState(globalState, nodeId, state, intoTarget, tracker.lengths());
            discover(nodeState, direction);
        } else if (globalState.searchMode == SearchMode.Bidirectional && !nodeState.hasBeenSeen(direction)) {
            // this branch means we continue expanding in both directions past the opposite frontier, if the node has
            // not been previously seen by *this* direction
            discover(nodeState, direction);
        }

        return nodeState;
    }

    private boolean cachedRelPredicate(
            Predicate<RelationshipTraversalEntities> predicate,
            RelationshipTraversalEntities rel,
            TraversalDirection dir) {
        if (predicate == RelationshipPredicate.ALWAYS_TRUE) {
            return true;
        }
        return relPredicateCache.getIfAbsentPut(
                new CachedRelPredicate(predicate, rel.relationshipReference(), dir), () -> {
                    this.mt.allocateHeap(CachedRelPredicate.SHALLOW_SIZE);
                    return predicate.test(dir.isForward() ? rel : new ReversedRelTraversalEntities(rel));
                });
    }

    private boolean cachedNodePredicate(LongPredicate predicate, long node) {
        if (predicate == Predicates.ALWAYS_TRUE_LONG) {
            return true;
        }
        return nodePredicateCache.getIfAbsentPut(new CachedNodePredicate(predicate, node), () -> {
            this.mt.allocateHeap(CachedNodePredicate.SHALLOW_SIZE);
            return predicate.test(node);
        });
    }

    public void expand() {
        foundNodes.openBuffer();
        var direction = foundNodes.getNextExpansionDirection();
        hooks.expand(direction, foundNodes);

        for (var pair : foundNodes.frontier(direction).keyValuesView()) {
            var dbNodeId = pair.getOne();
            var statesById = pair.getTwo();

            foundNodes.recordFrontierIteration(statesById);
            statesList.clear();
            for (var nodeState : statesById) {
                if (nodeState != null) {
                    statesList.add(nodeState.state());
                }
            }

            hooks.expandNode(dbNodeId, statesList, direction);

            pgCursor.setNodeAndStates(dbNodeId, statesList, direction);
            while (pgCursor.next()) {
                long foundNode = pgCursor.otherNodeReference();
                var re = pgCursor.relationshipExpansion();

                switch (direction) {
                    case FORWARD -> {
                        var nextNode = encounter(foundNode, re.targetState(), direction);
                        var node = foundNodes.getFromExpansionBucket(statesById, re.sourceState().id());

                        var signpost = TwoWaySignpost.fromRelExpansion(
                                mt,
                                node,
                                pgCursor.relationshipReference(),
                                nextNode,
                                re,
                                foundNodes.forwardDepth(),
                                tracker.lengths());

                        if (globalState.searchMode == SearchMode.Unidirectional
                                || !nextNode.hasSourceSignpost(signpost)) {
                            nextNode.addSourceSignpost(signpost, foundNodes.forwardDepth());
                        }
                    }

                    case BACKWARD -> {
                        var nextNode = encounter(foundNode, re.sourceState(), direction);
                        var node = foundNodes.getFromExpansionBucket(statesById, re.targetState().id());

                        var signpost = TwoWaySignpost.fromRelExpansion(
                                mt, nextNode, pgCursor.relationshipReference(), node, re, tracker.lengths());

                        if (!nextNode.hasTargetSignpost(signpost)) {
                            var addedSignpost = node.upsertSourceSignpost(signpost);
                            addedSignpost.setMinTargetDistance(
                                    foundNodes.backwardDepth(), PGPathPropagatingBFS.Phase.Expansion);
                        }
                    }
                }
            }
        }

        foundNodes.commitBuffer(direction);
    }

    public void setTracer(KernelReadTracer tracer) {
        pgCursor.setTracer(tracer);
    }

    @Override
    public void close() throws Exception {
        // globalState is not owned by this class; it should be closed by the consumer
        pgCursor.close();
        statesList.close();
    }

    private static class CachingRelCursor {

        private record CachedNode(long nodeId, int[] types, Direction direction) {
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                CachedNode that = (CachedNode) o;
                return nodeId == that.nodeId && Arrays.equals(types, that.types) && direction == that.direction;
            }

            @Override
            public int hashCode() {
                return Objects.hash(nodeId, Arrays.hashCode(types), direction);
            }

            public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(CachedNode.class);
        }

        private record CachedRel(
                long relationshipReference,
                int type,
                long sourceNodeReference,
                long targetNodeReference,
                long otherNodeReference,
                long originNodeReference)
                implements RelationshipTraversalEntities {
            public static final long SHALLOW_SIZE = HeapEstimator.shallowSizeOfInstance(CachedRel.class);
        }

        private final ProductGraphTraversalCursor.DataGraphRelationshipCursor relCursor;
        private final HeapTracking.Map<CachedNode, List<RelationshipTraversalEntities>> cache;
        private final MemoryTracker mt;

        public CachingRelCursor(ProductGraphTraversalCursor.DataGraphRelationshipCursor relCursor, MemoryTracker mt) {
            this.relCursor = relCursor;
            // we do not need to close this collection; the whole of PGPathPropagatingBFS uses a scoped memory tracker
            this.cache = HeapTrackingCollections.newMap(mt);
            this.mt = mt;
        }

        public Iterator<RelationshipTraversalEntities> iterator(long node, int[] types, Direction direction) {
            var cacheKey = new CachedNode(node, types, direction);
            var cached = cache.get(cacheKey);
            if (cached != null) {
                return cached.iterator();
            } else {
                HeapTrackingArrayList<RelationshipTraversalEntities> currentCache =
                        HeapTrackingCollections.newArrayList(this.mt);
                this.mt.allocateHeap(CachedNode.SHALLOW_SIZE);
                cache.put(cacheKey, currentCache);
                relCursor.setNode(node, RelationshipSelection.selection(types, direction));
                return new PrefetchingIterator<>() {
                    @Override
                    protected RelationshipTraversalEntities fetchNextOrNull() {
                        if (relCursor.nextRelationship()) {
                            var cached = new CachedRel(
                                    relCursor.relationshipReference(),
                                    relCursor.type(),
                                    relCursor.sourceNodeReference(),
                                    relCursor.targetNodeReference(),
                                    relCursor.otherNodeReference(),
                                    relCursor.originNodeReference());
                            mt.allocateHeap(CachedRel.SHALLOW_SIZE);
                            currentCache.add(cached);
                            return cached;
                        } else {
                            return null;
                        }
                    }
                };
            }
        }
    }
}
