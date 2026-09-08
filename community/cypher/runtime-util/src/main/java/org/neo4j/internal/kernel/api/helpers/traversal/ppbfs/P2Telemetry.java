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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * RESEARCH-ONLY P2 telemetry. Must never enter a production or contribution branch.
 *
 * <p>Measures the actual level-owned state-bucket distribution on the dense baseline:
 * per allocated {@code HeapTrackingArrayList<NodeState>} bucket it records {@code S}, occupied ids,
 * writes/duplicates, insertion order, canonical and frontier lookups by role, full iterations with
 * slots scanned vs active emitted, and lifetime in BFS epochs.
 *
 * <p>Enabled by setting {@code -Dorg.neo4j.ppbfs.p2.telemetryDir=<dir>}. When disabled, every hook is a
 * single static-boolean check. All aggregation is in memory; files are written once per run by
 * {@link #endRun()}. Never performs per-operation disk I/O. Single active run at a time; the synthetic
 * harness runs single-threaded.
 */
final class P2Telemetry {
    static final String DIRECTORY_PROPERTY = "org.neo4j.ppbfs.p2.telemetryDir";

    /** Lookup roles for canonical probes. */
    static final int ROLE_BUFFER = 0;
    static final int ROLE_FORWARD = 1;
    static final int ROLE_BACKWARD = 2;
    static final int ROLE_HISTORY = 3;

    static final boolean ENABLED = System.getProperty(DIRECTORY_PROPERTY) != null;

    private static Run current;

    private P2Telemetry() {}

    static final class Bucket {
        long nodeId = -1;
        int s;
        int allocDepth;
        int lastTouchDepth;
        long writes;
        long duplicateWrites;
        final BitSet ids = new BitSet();
        int lastWrittenId = Integer.MIN_VALUE;
        boolean insertionMonotonic = true;
        long adjacentWrites;
        long canonHits;
        long canonMisses;
        long frontierHits;
        long frontierMisses;
        long iterations;
        long forwardIterations;
        long backwardIterations;
        long slotsScanned;
        long activeEmitted;
        int kAtFirstIteration = -1;

        int kFinal() {
            return ids.cardinality();
        }

        int minId() {
            return ids.isEmpty() ? -1 : ids.nextSetBit(0);
        }

        int maxId() {
            return ids.isEmpty() ? -1 : ids.length() - 1;
        }

        int span() {
            return ids.isEmpty() ? 0 : maxId() - minId() + 1;
        }

        int runs() {
            int runs = 0;
            for (int i = ids.nextSetBit(0); i >= 0; ) {
                runs++;
                i = ids.nextClearBit(i);
                i = ids.nextSetBit(i);
            }
            return runs;
        }

        int occupiedChunks(int chunk) {
            int count = 0;
            for (int i = ids.nextSetBit(0); i >= 0; i = ids.nextSetBit(i + 1)) {
                int block = i / chunk;
                count++;
                i = (block + 1) * chunk - 1;
            }
            return count;
        }
    }

    static final class Run {
        final String workloadId;
        final int nfaStateCount;
        final String searchMode;
        final IdentityHashMap<Object, Bucket> buckets = new IdentityHashMap<>();
        long nodeMissesBuffer;
        long nodeMissesForward;
        long nodeMissesBackward;
        long nodeMissesHistory;
        long historyLevelProbes;
        final long[] canonHitsByRole = new long[4];
        final long[] canonMissesByRole = new long[4];

        Run(String workloadId, int nfaStateCount, String searchMode) {
            this.workloadId = workloadId;
            this.nfaStateCount = nfaStateCount;
            this.searchMode = searchMode;
        }
    }

    static synchronized void beginRun(String workloadId, int nfaStateCount, SearchMode mode) {
        if (!ENABLED) {
            return;
        }
        current = new Run(workloadId, nfaStateCount, mode.name());
    }

    static synchronized Run currentRun() {
        return current;
    }

    /** Ends the run and writes {@code summary.csv} + {@code buckets.csv} into a fresh timestamped directory. */
    static synchronized Path endRun() {
        if (!ENABLED || current == null) {
            return null;
        }
        Run run = current;
        current = null;
        try {
            Path base = Path.of(System.getProperty(DIRECTORY_PROPERTY));
            String stamp = Instant.now().toString().replace(':', '-');
            Path dir = base.resolve(run.workloadId + "-" + stamp);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("buckets.csv"), bucketsCsv(run), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("summary.csv"), summaryCsv(run), StandardCharsets.UTF_8);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Bucket bucketOf(Run run, Object bucketList) {
        return run.buckets.get(bucketList);
    }

    static void onAlloc(Object bucketList, long nodeId, int s, int depth) {
        if (!ENABLED) {
            return;
        }
        Run run = current;
        if (run == null) {
            return;
        }
        Bucket b = new Bucket();
        b.nodeId = nodeId;
        b.s = s;
        b.allocDepth = depth;
        b.lastTouchDepth = depth;
        run.buckets.put(bucketList, b);
    }

    static void onWrite(Object bucketList, int stateId, boolean duplicate, int depth) {
        if (!ENABLED) {
            return;
        }
        Run run = current;
        if (run == null) {
            return;
        }
        Bucket b = bucketOf(run, bucketList);
        if (b == null) {
            return;
        }
        b.writes++;
        b.lastTouchDepth = depth;
        if (duplicate) {
            b.duplicateWrites++;
            return;
        }
        if (b.lastWrittenId != Integer.MIN_VALUE) {
            if (stateId < b.lastWrittenId) {
                b.insertionMonotonic = false;
            }
            if (Math.abs(stateId - b.lastWrittenId) == 1) {
                b.adjacentWrites++;
            }
        }
        b.lastWrittenId = stateId;
        b.ids.set(stateId);
    }

    static void onCanonicalProbe(Object bucketList, boolean hit, int role, int depth) {
        if (!ENABLED) {
            return;
        }
        Run run = current;
        if (run == null) {
            return;
        }
        if (role == ROLE_HISTORY) {
            run.historyLevelProbes++;
        }
        if (bucketList == null) {
            switch (role) {
                case ROLE_BUFFER -> run.nodeMissesBuffer++;
                case ROLE_FORWARD -> run.nodeMissesForward++;
                case ROLE_BACKWARD -> run.nodeMissesBackward++;
                default -> run.nodeMissesHistory++;
            }
            return;
        }
        Bucket b = bucketOf(run, bucketList);
        if (b == null) {
            return;
        }
        b.lastTouchDepth = depth;
        if (hit) {
            b.canonHits++;
            run.canonHitsByRole[role]++;
        } else {
            b.canonMisses++;
            run.canonMissesByRole[role]++;
        }
    }

    static void onFrontierLookup(Object bucketList, boolean hit, int depth) {
        if (!ENABLED) {
            return;
        }
        Run run = current;
        if (run == null) {
            return;
        }
        Bucket b = bucketOf(run, bucketList);
        if (b == null) {
            return;
        }
        b.lastTouchDepth = depth;
        if (hit) {
            b.frontierHits++;
        } else {
            b.frontierMisses++;
        }
    }

    static void onIteration(Object bucketList, int slotsScanned, int activeEmitted, boolean forward, int depth) {
        if (!ENABLED) {
            return;
        }
        Run run = current;
        if (run == null) {
            return;
        }
        Bucket b = bucketOf(run, bucketList);
        if (b == null) {
            return;
        }
        b.iterations++;
        if (forward) {
            b.forwardIterations++;
        } else {
            b.backwardIterations++;
        }
        b.slotsScanned += slotsScanned;
        b.activeEmitted += activeEmitted;
        b.lastTouchDepth = depth;
        if (b.kAtFirstIteration < 0) {
            b.kAtFirstIteration = activeEmitted;
        }
    }

    private static String bucketsCsv(Run run) {
        StringBuilder sb = new StringBuilder();
        sb.append("nodeId,S,kFinal,kFirst,minId,maxId,span,runs,occ8,occ16,occ32,writes,dups,monotonic,adjacent,")
                .append("canonHits,canonMisses,frontierHits,frontierMisses,iterations,slotsScanned,activeEmitted,")
                .append("allocDepth,lastTouchDepth\n");
        for (Bucket b : run.buckets.values()) {
            sb.append(b.nodeId).append(',')
                    .append(b.s).append(',')
                    .append(b.kFinal()).append(',')
                    .append(b.kAtFirstIteration).append(',')
                    .append(b.minId()).append(',')
                    .append(b.maxId()).append(',')
                    .append(b.span()).append(',')
                    .append(b.runs()).append(',')
                    .append(b.occupiedChunks(8)).append(',')
                    .append(b.occupiedChunks(16)).append(',')
                    .append(b.occupiedChunks(32)).append(',')
                    .append(b.writes).append(',')
                    .append(b.duplicateWrites).append(',')
                    .append(b.insertionMonotonic ? 1 : 0).append(',')
                    .append(b.adjacentWrites).append(',')
                    .append(b.canonHits).append(',')
                    .append(b.canonMisses).append(',')
                    .append(b.frontierHits).append(',')
                    .append(b.frontierMisses).append(',')
                    .append(b.iterations).append(',')
                    .append(b.slotsScanned).append(',')
                    .append(b.activeEmitted).append(',')
                    .append(b.allocDepth).append(',')
                    .append(b.lastTouchDepth).append('\n');
        }
        return sb.toString();
    }

    private static String summaryCsv(Run run) {
        int n = run.buckets.size();
        long writes = 0, dups = 0, canonHits = 0, canonMisses = 0, frontierHits = 0, frontierMisses = 0;
        long iterations = 0, slots = 0, active = 0, monotonic = 0, adjacent = 0;
        List<Double> occupancy = new ArrayList<>(n);
        Map<String, Integer> kHist = new TreeMap<>();
        long nullSlots = 0;
        for (Bucket b : run.buckets.values()) {
            writes += b.writes;
            dups += b.duplicateWrites;
            canonHits += b.canonHits;
            canonMisses += b.canonMisses;
            frontierHits += b.frontierHits;
            frontierMisses += b.frontierMisses;
            iterations += b.iterations;
            slots += b.slotsScanned;
            active += b.activeEmitted;
            if (b.insertionMonotonic) {
                monotonic++;
            }
            adjacent += b.adjacentWrites;
            if (b.s > 0) {
                occupancy.add(b.kFinal() / (double) b.s);
            }
            kHist.merge("k=" + b.kFinal(), 1, Integer::sum);
        }
        nullSlots = slots - active;
        occupancy.sort(Double::compareTo);
        double p50 = quantile(occupancy, 0.50);
        double p95 = quantile(occupancy, 0.95);
        double mean = occupancy.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        StringBuilder sb = new StringBuilder();
        sb.append("metric,value\n");
        row(sb, "workload", run.workloadId);
        row(sb, "S", Integer.toString(run.nfaStateCount));
        row(sb, "searchMode", run.searchMode);
        row(sb, "buckets", Long.toString(n));
        row(sb, "writes", Long.toString(writes));
        row(sb, "duplicateWrites", Long.toString(dups));
        row(sb, "canonHits", Long.toString(canonHits));
        row(sb, "canonMisses", Long.toString(canonMisses));
        String[] roleNames = {"Buffer", "Forward", "Backward", "History"};
        for (int r = 0; r < 4; r++) {
            row(sb, "canonHit" + roleNames[r], Long.toString(run.canonHitsByRole[r]));
            row(sb, "canonMiss" + roleNames[r], Long.toString(run.canonMissesByRole[r]));
        }
        row(sb, "nodeMissBuffer", Long.toString(run.nodeMissesBuffer));
        row(sb, "nodeMissForward", Long.toString(run.nodeMissesForward));
        row(sb, "nodeMissBackward", Long.toString(run.nodeMissesBackward));
        row(sb, "nodeMissHistory", Long.toString(run.nodeMissesHistory));
        row(sb, "historyLevelProbes", Long.toString(run.historyLevelProbes));
        row(sb, "frontierHits", Long.toString(frontierHits));
        row(sb, "frontierMisses", Long.toString(frontierMisses));
        row(sb, "iterations", Long.toString(iterations));
        row(sb, "slotsScanned", Long.toString(slots));
        row(sb, "activeEmitted", Long.toString(active));
        row(sb, "nullSlotsScanned", Long.toString(nullSlots));
        row(sb, "nullFractionOfScans", slots == 0 ? "NaN" : String.format(Locale.ROOT, "%.4f", nullSlots / (double) slots));
        row(sb, "meanOccupancy", String.format(Locale.ROOT, "%.4f", mean));
        row(sb, "p50Occupancy", String.format(Locale.ROOT, "%.4f", p50));
        row(sb, "p95Occupancy", String.format(Locale.ROOT, "%.4f", p95));
        row(sb, "monotonicBuckets", Long.toString(monotonic));
        row(sb, "adjacentWrites", Long.toString(adjacent));
        for (Map.Entry<String, Integer> e : kHist.entrySet()) {
            row(sb, "hist_" + e.getKey(), e.getValue().toString());
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, String metric, String value) {
        sb.append(metric).append(',').append(value).append('\n');
    }

    private static double quantile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) {
            return Double.NaN;
        }
        int idx = Math.min(sorted.size() - 1, (int) Math.floor(q * sorted.size()));
        return sorted.get(idx);
    }
}
