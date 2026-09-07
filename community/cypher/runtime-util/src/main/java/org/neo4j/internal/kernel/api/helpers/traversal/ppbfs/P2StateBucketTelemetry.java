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

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.collection.trackable.HeapTrackingArrayList;
import org.neo4j.collection.trackable.HeapTrackingLongObjectHashMap;

/**
 * Research-only aggregate telemetry for the concrete level-owned NFA-state buckets in {@link FoundNodes}.
 *
 * <p>This class must not be copied into an upstream contribution. The telemetry build is not valid for latency,
 * allocation, GC, tracked-memory, or memory-limit comparisons.
 */
final class P2StateBucketTelemetry implements AutoCloseable {
    static final String OUTPUT_PROPERTY = "ppbfs.p2.bucket-metrics.output";
    static final String RUN_ID_PROPERTY = "ppbfs.p2.bucket-metrics.run-id";
    static final int SCHEMA_VERSION = 1;
    static final String CAUSAL_BASELINE_SHA = "f213380f812b820a1b312e2ea52cb3d8f1931ccc";

    private static final AtomicLong QUERY_SEQUENCE = new AtomicLong();
    private static final Object OUTPUT_LOCK = new Object();

    enum LookupRole {
        BUFFER,
        FORWARD_FRONTIER,
        BACKWARD_FRONTIER,
        HISTORY,
        EXPANSION
    }

    private final boolean enabled;
    private final Path output;
    private final String runId;
    private final SearchMode searchMode;
    private final int nfaStateCount;
    private final long querySequence;
    private final Instant startedAt;

    private final IdentityHashMap<HeapTrackingArrayList<NodeState>, PendingBucket> pendingBuckets;
    private final IdentityHashMap<HeapTrackingArrayList<NodeState>, Integer> finalOccupancy;
    private final IdentityHashMap<
                    HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>>, LevelProfile>
            activeLevels;

    private final long[] occupancyHistogram;
    private final long[] spanHistogram;
    private final long[] runCountHistogram;
    private final long[] chunks8Histogram;
    private final long[] chunks16Histogram;
    private final long[] chunks32Histogram;
    private final long[] minimumStateIdHistogram;
    private final long[] maximumStateIdHistogram;
    private final long[] activeStateIdHistogram;
    private final long[] writesByOccupancy;
    private final long[] duplicateWritesByOccupancy;
    private final long[] insertionTransitionsByOccupancy;
    private final long[] nondecreasingInsertionsByOccupancy;
    private final long[] adjacentInsertionsByOccupancy;
    private final long[] iterationsByOccupancy;
    private final long[][] bucketLookupsByRoleAndOccupancy;
    private final long[][] bucketLookupHitsByRoleAndOccupancy;
    private final long[] levelProbesByRole;
    private final long[] mapGetsByRole;
    private final long[] mapBucketPresentByRole;

    private final Map<Integer, Long> bufferLifetimeHistogram;
    private final Map<Integer, Long> globalLifetimeHistogram;
    private final Map<Integer, Long> directionLifetimeHistogram;

    private long createdBuckets;
    private long committedBuckets;
    private long uncommittedBuckets;
    private long activeSlots;
    private long denseSlots;
    private long fullIterations;
    private long iterationSlotsScanned;
    private long iterationActiveStates;
    private long iterationNullSlots;
    private long contiguousBuckets;
    private long lowContiguousBuckets;
    private long unknownBucketOperations;
    private long openLevelBuckets;
    private boolean closed;

    static P2StateBucketTelemetry configured(int nfaStateCount, SearchMode searchMode) {
        var output = System.getProperty(OUTPUT_PROPERTY);
        if (output == null || output.isBlank()) {
            return new P2StateBucketTelemetry(nfaStateCount, searchMode);
        }
        var configuredRunId = System.getProperty(RUN_ID_PROPERTY);
        var runId = configuredRunId == null || configuredRunId.isBlank()
                ? UUID.randomUUID().toString()
                : configuredRunId;
        return new P2StateBucketTelemetry(Path.of(output), nfaStateCount, searchMode, runId);
    }

    private P2StateBucketTelemetry(int nfaStateCount, SearchMode searchMode) {
        this.enabled = false;
        this.output = null;
        this.runId = "";
        this.searchMode = searchMode;
        this.nfaStateCount = nfaStateCount;
        this.querySequence = -1;
        this.startedAt = null;
        this.pendingBuckets = null;
        this.finalOccupancy = null;
        this.activeLevels = null;
        this.occupancyHistogram = null;
        this.spanHistogram = null;
        this.runCountHistogram = null;
        this.chunks8Histogram = null;
        this.chunks16Histogram = null;
        this.chunks32Histogram = null;
        this.minimumStateIdHistogram = null;
        this.maximumStateIdHistogram = null;
        this.activeStateIdHistogram = null;
        this.writesByOccupancy = null;
        this.duplicateWritesByOccupancy = null;
        this.insertionTransitionsByOccupancy = null;
        this.nondecreasingInsertionsByOccupancy = null;
        this.adjacentInsertionsByOccupancy = null;
        this.iterationsByOccupancy = null;
        this.bucketLookupsByRoleAndOccupancy = null;
        this.bucketLookupHitsByRoleAndOccupancy = null;
        this.levelProbesByRole = null;
        this.mapGetsByRole = null;
        this.mapBucketPresentByRole = null;
        this.bufferLifetimeHistogram = null;
        this.globalLifetimeHistogram = null;
        this.directionLifetimeHistogram = null;
    }

    P2StateBucketTelemetry(Path output, int nfaStateCount, SearchMode searchMode, String runId) {
        if (nfaStateCount <= 0) {
            throw new IllegalArgumentException("NFA state count must be positive, was " + nfaStateCount);
        }
        this.enabled = true;
        this.output = output;
        this.runId = runId;
        this.searchMode = searchMode;
        this.nfaStateCount = nfaStateCount;
        this.querySequence = QUERY_SEQUENCE.incrementAndGet();
        this.startedAt = Instant.now();
        this.pendingBuckets = new IdentityHashMap<>();
        this.finalOccupancy = new IdentityHashMap<>();
        this.activeLevels = new IdentityHashMap<>();
        this.occupancyHistogram = new long[nfaStateCount + 1];
        this.spanHistogram = new long[nfaStateCount + 1];
        this.runCountHistogram = new long[nfaStateCount + 1];
        this.chunks8Histogram = new long[nfaStateCount + 1];
        this.chunks16Histogram = new long[nfaStateCount + 1];
        this.chunks32Histogram = new long[nfaStateCount + 1];
        this.minimumStateIdHistogram = new long[nfaStateCount];
        this.maximumStateIdHistogram = new long[nfaStateCount];
        this.activeStateIdHistogram = new long[nfaStateCount];
        this.writesByOccupancy = new long[nfaStateCount + 1];
        this.duplicateWritesByOccupancy = new long[nfaStateCount + 1];
        this.insertionTransitionsByOccupancy = new long[nfaStateCount + 1];
        this.nondecreasingInsertionsByOccupancy = new long[nfaStateCount + 1];
        this.adjacentInsertionsByOccupancy = new long[nfaStateCount + 1];
        this.iterationsByOccupancy = new long[nfaStateCount + 1];
        this.bucketLookupsByRoleAndOccupancy = new long[LookupRole.values().length][nfaStateCount + 1];
        this.bucketLookupHitsByRoleAndOccupancy = new long[LookupRole.values().length][nfaStateCount + 1];
        this.levelProbesByRole = new long[LookupRole.values().length];
        this.mapGetsByRole = new long[LookupRole.values().length];
        this.mapBucketPresentByRole = new long[LookupRole.values().length];
        this.bufferLifetimeHistogram = new java.util.TreeMap<>();
        this.globalLifetimeHistogram = new java.util.TreeMap<>();
        this.directionLifetimeHistogram = new java.util.TreeMap<>();
    }

    void recordBucketCreated(HeapTrackingArrayList<NodeState> bucket, int totalDepth) {
        if (!enabled) {
            return;
        }
        var previous = pendingBuckets.put(bucket, new PendingBucket(totalDepth));
        if (previous != null || finalOccupancy.containsKey(bucket)) {
            unknownBucketOperations++;
        }
        createdBuckets++;
    }

    void recordWrite(HeapTrackingArrayList<NodeState> bucket, int stateId) {
        if (!enabled) {
            return;
        }
        var pending = pendingBuckets.get(bucket);
        if (pending == null) {
            unknownBucketOperations++;
            return;
        }

        pending.writes++;
        if (bucket.get(stateId) != null) {
            pending.duplicateWrites++;
            return;
        }

        if (pending.lastDistinctStateId >= 0) {
            pending.distinctInsertionTransitions++;
            if (stateId >= pending.lastDistinctStateId) {
                pending.nondecreasingDistinctInsertions++;
            }
            if (Math.abs(stateId - pending.lastDistinctStateId) == 1) {
                pending.adjacentDistinctInsertions++;
            }
        }
        pending.lastDistinctStateId = stateId;
    }

    void recordLevelProbe(LookupRole role, boolean mapGetPerformed, boolean bucketPresent) {
        if (!enabled) {
            return;
        }
        levelProbesByRole[role.ordinal()]++;
        if (mapGetPerformed) {
            mapGetsByRole[role.ordinal()]++;
        }
        if (bucketPresent) {
            mapBucketPresentByRole[role.ordinal()]++;
        }
    }

    void recordBucketLookup(HeapTrackingArrayList<NodeState> bucket, LookupRole role, boolean hit) {
        if (!enabled) {
            return;
        }

        if (role == LookupRole.BUFFER) {
            var pending = pendingBuckets.get(bucket);
            if (pending != null) {
                pending.bufferLookups++;
                if (hit) {
                    pending.bufferLookupHits++;
                }
                return;
            }
        }

        var occupancy = finalOccupancy.get(bucket);
        if (occupancy == null) {
            unknownBucketOperations++;
            return;
        }
        bucketLookupsByRoleAndOccupancy[role.ordinal()][occupancy]++;
        if (hit) {
            bucketLookupHitsByRoleAndOccupancy[role.ordinal()][occupancy]++;
        }
    }

    void recordBufferCommitted(
            HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>> level,
            TraversalDirection direction,
            int totalDepth,
            int directionDepth) {
        if (!enabled || level.isEmpty()) {
            return;
        }

        var profile = new LevelProfile(direction, totalDepth, directionDepth, level.size());
        if (activeLevels.put(level, profile) != null) {
            unknownBucketOperations++;
        }
        level.forEachValue(bucket -> finalizeBucket(bucket, true, totalDepth));
    }

    void recordFrontierRetired(
            HeapTrackingLongObjectHashMap<HeapTrackingArrayList<NodeState>> level,
            TraversalDirection direction,
            int totalDepth,
            int directionDepth) {
        if (!enabled || level.isEmpty()) {
            return;
        }

        var profile = activeLevels.remove(level);
        if (profile == null) {
            unknownBucketOperations++;
            return;
        }
        if (profile.direction != direction) {
            unknownBucketOperations++;
        }
        merge(globalLifetimeHistogram, Math.max(0, totalDepth - profile.totalDepth), profile.bucketCount);
        merge(
                directionLifetimeHistogram,
                Math.max(0, directionDepth - profile.directionDepth),
                profile.bucketCount);
    }

    void recordFullIteration(HeapTrackingArrayList<NodeState> bucket) {
        if (!enabled) {
            return;
        }
        var occupancy = finalOccupancy.get(bucket);
        if (occupancy == null) {
            unknownBucketOperations++;
            return;
        }

        fullIterations++;
        iterationsByOccupancy[occupancy]++;
        iterationSlotsScanned += nfaStateCount;
        iterationActiveStates += occupancy;
        iterationNullSlots += nfaStateCount - occupancy;
    }

    private void finalizeBucket(
            HeapTrackingArrayList<NodeState> bucket, boolean committed, int committedAtTotalDepth) {
        if (finalOccupancy.containsKey(bucket)) {
            return;
        }

        var pending = pendingBuckets.remove(bucket);
        if (pending == null) {
            unknownBucketOperations++;
            pending = new PendingBucket(-1);
        }

        int occupancy = 0;
        int minimumStateId = nfaStateCount;
        int maximumStateId = -1;
        int runs = 0;
        int previousActiveId = -2;
        int chunks8 = 0;
        int chunks16 = 0;
        int chunks32 = 0;
        int previousChunk8 = -1;
        int previousChunk16 = -1;
        int previousChunk32 = -1;

        for (int stateId = 0; stateId < nfaStateCount; stateId++) {
            if (bucket.get(stateId) == null) {
                continue;
            }

            occupancy++;
            activeStateIdHistogram[stateId]++;
            minimumStateId = Math.min(minimumStateId, stateId);
            maximumStateId = stateId;
            if (stateId != previousActiveId + 1) {
                runs++;
            }
            previousActiveId = stateId;

            int chunk8 = stateId / 8;
            if (chunk8 != previousChunk8) {
                chunks8++;
                previousChunk8 = chunk8;
            }
            int chunk16 = stateId / 16;
            if (chunk16 != previousChunk16) {
                chunks16++;
                previousChunk16 = chunk16;
            }
            int chunk32 = stateId / 32;
            if (chunk32 != previousChunk32) {
                chunks32++;
                previousChunk32 = chunk32;
            }
        }

        finalOccupancy.put(bucket, occupancy);
        occupancyHistogram[occupancy]++;
        denseSlots += nfaStateCount;
        activeSlots += occupancy;

        if (occupancy > 0) {
            int span = maximumStateId - minimumStateId + 1;
            spanHistogram[span]++;
            runCountHistogram[runs]++;
            chunks8Histogram[chunks8]++;
            chunks16Histogram[chunks16]++;
            chunks32Histogram[chunks32]++;
            minimumStateIdHistogram[minimumStateId]++;
            maximumStateIdHistogram[maximumStateId]++;
            if (runs == 1) {
                contiguousBuckets++;
            }
            if (minimumStateId == 0 && span == occupancy) {
                lowContiguousBuckets++;
            }
        }

        writesByOccupancy[occupancy] += pending.writes;
        duplicateWritesByOccupancy[occupancy] += pending.duplicateWrites;
        insertionTransitionsByOccupancy[occupancy] += pending.distinctInsertionTransitions;
        nondecreasingInsertionsByOccupancy[occupancy] += pending.nondecreasingDistinctInsertions;
        adjacentInsertionsByOccupancy[occupancy] += pending.adjacentDistinctInsertions;
        bucketLookupsByRoleAndOccupancy[LookupRole.BUFFER.ordinal()][occupancy] += pending.bufferLookups;
        bucketLookupHitsByRoleAndOccupancy[LookupRole.BUFFER.ordinal()][occupancy] +=
                pending.bufferLookupHits;

        if (committed) {
            committedBuckets++;
            if (pending.createdAtTotalDepth >= 0) {
                merge(
                        bufferLifetimeHistogram,
                        Math.max(0, committedAtTotalDepth - pending.createdAtTotalDepth),
                        1);
            }
        } else {
            uncommittedBuckets++;
        }
    }

    @Override
    public void close() {
        if (!enabled || closed) {
            return;
        }
        closed = true;

        var stillPending = new ArrayList<>(pendingBuckets.keySet());
        for (var bucket : stillPending) {
            finalizeBucket(bucket, false, -1);
        }

        for (var profile : activeLevels.values()) {
            openLevelBuckets += profile.bucketCount;
        }

        var finishedAt = Instant.now();
        var json = toJson(finishedAt);
        synchronized (OUTPUT_LOCK) {
            try {
                var parent = output.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        output,
                        json + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (java.io.IOException e) {
                throw new UncheckedIOException("Failed to append P2 bucket telemetry to " + output, e);
            }
        }

        pendingBuckets.clear();
        finalOccupancy.clear();
        activeLevels.clear();
    }

    private String toJson(Instant finishedAt) {
        var json = new StringBuilder(16_384);
        json.append('{');
        appendNumber(json, "schemaVersion", SCHEMA_VERSION);
        appendString(json, "evidence", "MEASURED");
        appendString(json, "causalBaselineSha", CAUSAL_BASELINE_SHA);
        appendNumber(json, "querySequence", querySequence);
        appendString(json, "runId", runId);
        appendString(json, "searchMode", searchMode.name());
        appendString(json, "startedAt", startedAt.toString());
        appendString(json, "finishedAt", finishedAt.toString());
        appendNumber(json, "nfaStateCount", nfaStateCount);
        appendNumber(json, "createdBuckets", createdBuckets);
        appendNumber(json, "committedBuckets", committedBuckets);
        appendNumber(json, "uncommittedBuckets", uncommittedBuckets);
        appendNumber(json, "activeSlots", activeSlots);
        appendNumber(json, "denseSlots", denseSlots);
        appendNumber(json, "unusedDenseSlots", denseSlots - activeSlots);
        appendNumber(json, "fullIterations", fullIterations);
        appendNumber(json, "iterationSlotsScanned", iterationSlotsScanned);
        appendNumber(json, "iterationActiveStates", iterationActiveStates);
        appendNumber(json, "iterationNullSlots", iterationNullSlots);
        appendNumber(json, "contiguousBuckets", contiguousBuckets);
        appendNumber(json, "lowContiguousBuckets", lowContiguousBuckets);
        appendNumber(json, "unknownBucketOperations", unknownBucketOperations);
        appendNumber(json, "openLevelCount", activeLevels.size());
        appendNumber(json, "openLevelBuckets", openLevelBuckets);

        appendHistogram(json, "occupancyHistogram", occupancyHistogram);
        appendHistogram(json, "spanHistogram", spanHistogram);
        appendHistogram(json, "runCountHistogram", runCountHistogram);
        appendHistogram(json, "chunks8Histogram", chunks8Histogram);
        appendHistogram(json, "chunks16Histogram", chunks16Histogram);
        appendHistogram(json, "chunks32Histogram", chunks32Histogram);
        appendHistogram(json, "minimumStateIdHistogram", minimumStateIdHistogram);
        appendHistogram(json, "maximumStateIdHistogram", maximumStateIdHistogram);
        appendHistogram(json, "activeStateIdHistogram", activeStateIdHistogram);
        appendHistogram(json, "writesByOccupancy", writesByOccupancy);
        appendHistogram(json, "duplicateWritesByOccupancy", duplicateWritesByOccupancy);
        appendHistogram(json, "insertionTransitionsByOccupancy", insertionTransitionsByOccupancy);
        appendHistogram(json, "nondecreasingInsertionsByOccupancy", nondecreasingInsertionsByOccupancy);
        appendHistogram(json, "adjacentInsertionsByOccupancy", adjacentInsertionsByOccupancy);
        appendHistogram(json, "iterationsByOccupancy", iterationsByOccupancy);
        appendRoleCounts(json, "levelProbesByRole", levelProbesByRole);
        appendRoleCounts(json, "mapGetsByRole", mapGetsByRole);
        appendRoleCounts(json, "mapBucketPresentByRole", mapBucketPresentByRole);
        appendRoleHistograms(json, "bucketLookupsByRoleAndOccupancy", bucketLookupsByRoleAndOccupancy);
        appendRoleHistograms(
                json, "bucketLookupHitsByRoleAndOccupancy", bucketLookupHitsByRoleAndOccupancy);
        appendMapHistogram(json, "bufferDepthLifetimeHistogram", bufferLifetimeHistogram);
        appendMapHistogram(json, "globalDepthLifetimeHistogram", globalLifetimeHistogram);
        appendMapHistogram(json, "directionDepthLifetimeHistogram", directionLifetimeHistogram);

        removeTrailingComma(json);
        json.append('}');
        return json.toString();
    }

    private static void appendNumber(StringBuilder json, String name, long value) {
        appendFieldName(json, name);
        json.append(value).append(',');
    }

    private static void appendString(StringBuilder json, String name, String value) {
        appendFieldName(json, name);
        appendQuoted(json, value);
        json.append(',');
    }

    private static void appendHistogram(StringBuilder json, String name, long[] histogram) {
        appendFieldName(json, name);
        json.append('{');
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] == 0) {
                continue;
            }
            appendQuoted(json, Integer.toString(i));
            json.append(':').append(histogram[i]).append(',');
        }
        removeTrailingComma(json);
        json.append("},");
    }

    private static void appendRoleCounts(StringBuilder json, String name, long[] values) {
        appendFieldName(json, name);
        json.append('{');
        for (var role : LookupRole.values()) {
            appendQuoted(json, role.name());
            json.append(':').append(values[role.ordinal()]).append(',');
        }
        removeTrailingComma(json);
        json.append("},");
    }

    private static void appendRoleHistograms(StringBuilder json, String name, long[][] values) {
        appendFieldName(json, name);
        json.append('{');
        for (var role : LookupRole.values()) {
            appendQuoted(json, role.name());
            json.append(":{");
            var histogram = values[role.ordinal()];
            for (int occupancy = 0; occupancy < histogram.length; occupancy++) {
                if (histogram[occupancy] == 0) {
                    continue;
                }
                appendQuoted(json, Integer.toString(occupancy));
                json.append(':').append(histogram[occupancy]).append(',');
            }
            removeTrailingComma(json);
            json.append("},");
        }
        removeTrailingComma(json);
        json.append("},");
    }

    private static void appendMapHistogram(StringBuilder json, String name, Map<Integer, Long> histogram) {
        appendFieldName(json, name);
        json.append('{');
        for (var entry : histogram.entrySet()) {
            appendQuoted(json, Integer.toString(entry.getKey()));
            json.append(':').append(entry.getValue()).append(',');
        }
        removeTrailingComma(json);
        json.append("},");
    }

    private static void appendFieldName(StringBuilder json, String name) {
        appendQuoted(json, name);
        json.append(':');
    }

    private static void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        json.append(String.format("\\u%04x", (int) ch));
                    } else {
                        json.append(ch);
                    }
                }
            }
        }
        json.append('"');
    }

    private static void removeTrailingComma(StringBuilder json) {
        if (!json.isEmpty() && json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
    }

    private static void merge(Map<Integer, Long> histogram, int key, long count) {
        histogram.merge(key, count, Long::sum);
    }

    private static final class PendingBucket {
        final int createdAtTotalDepth;
        long writes;
        long duplicateWrites;
        long bufferLookups;
        long bufferLookupHits;
        long distinctInsertionTransitions;
        long nondecreasingDistinctInsertions;
        long adjacentDistinctInsertions;
        int lastDistinctStateId = -1;

        PendingBucket(int createdAtTotalDepth) {
            this.createdAtTotalDepth = createdAtTotalDepth;
        }
    }

    private record LevelProfile(
            TraversalDirection direction, int totalDepth, int directionDepth, int bucketCount) {}
}
