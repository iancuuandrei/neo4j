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
package org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.hooks

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

/** Research-only aggregate C4 observability. Never include this class in a contribution patch. */
final class C4MetricsPPBFSHooks(output: String) extends PPBFSHooks {
  private var activated = false
  private var activationCount = 0
  private var activationDepth = -1
  private var frozenHistorySize = 0
  private var lookupCountBeforeActivation = 0L
  private var postActivationLookups = 0L
  private var retiredIndexHits = 0L
  private var retiredIndexMisses = 0L
  private var frozenHistoryProbes = 0L
  private var transferredBuckets = 0L
  private var mergedBuckets = 0L
  private var canonicalBucketAllocations = 0L
  private var maxRetiredIndexSize = 0
  private var outerMapTransfers = 0

  override def foundNodesC4Activation(
    depth: Int,
    frozenHistory: Int,
    lookupsBefore: Long,
    retiringBuckets: Int,
    outerMapTransferred: Boolean
  ): Unit = synchronized {
    activated = true
    activationCount += 1
    activationDepth = depth
    frozenHistorySize = frozenHistory
    lookupCountBeforeActivation = lookupsBefore
    if (outerMapTransferred) outerMapTransfers += 1
  }

  override def foundNodesC4Retirement(
    transferred: Int,
    merged: Int,
    allocated: Int,
    retiredIndexSize: Int
  ): Unit = synchronized {
    transferredBuckets += transferred
    mergedBuckets += merged
    canonicalBucketAllocations += allocated
    maxRetiredIndexSize = math.max(maxRetiredIndexSize, retiredIndexSize)
  }

  override def foundNodesC4Lookup(retiredIndexHit: Boolean, historyProbes: Int): Unit = synchronized {
    postActivationLookups += 1
    if (retiredIndexHit) retiredIndexHits += 1 else retiredIndexMisses += 1
    frozenHistoryProbes += historyProbes
  }

  override def finished(): Unit = synchronized {
    val json =
      s"{\"activated\":$activated,\"activationCount\":$activationCount," +
        s"\"activationDepth\":$activationDepth,\"frozenHistorySize\":$frozenHistorySize," +
        s"\"lookupCountBeforeActivation\":$lookupCountBeforeActivation," +
        s"\"postActivationLookups\":$postActivationLookups,\"retiredIndexHits\":$retiredIndexHits," +
        s"\"retiredIndexMisses\":$retiredIndexMisses,\"frozenHistoryProbes\":$frozenHistoryProbes," +
        s"\"transferredBuckets\":$transferredBuckets,\"mergedBuckets\":$mergedBuckets," +
        s"\"canonicalBucketAllocations\":$canonicalBucketAllocations," +
        s"\"maxRetiredIndexSize\":$maxRetiredIndexSize,\"outerMapTransfers\":$outerMapTransfers}\n"
    val path = Path.of(output)
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    activated = false
    activationCount = 0
    activationDepth = -1
    frozenHistorySize = 0
    lookupCountBeforeActivation = 0L
    postActivationLookups = 0L
    retiredIndexHits = 0L
    retiredIndexMisses = 0L
    frozenHistoryProbes = 0L
    transferredBuckets = 0L
    mergedBuckets = 0L
    canonicalBucketAllocations = 0L
    maxRetiredIndexSize = 0
    outerMapTransfers = 0
  }
}
