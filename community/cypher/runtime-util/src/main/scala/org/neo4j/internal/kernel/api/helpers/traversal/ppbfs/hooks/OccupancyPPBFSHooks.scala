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

import org.neo4j.internal.kernel.api.helpers.traversal.ppbfs.FoundNodes.LookupLocation

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.collection.mutable

/** Research-only aggregate observability. Never include this class in a contribution patch. */
final class OccupancyPPBFSHooks(output: String) extends PPBFSHooks {
  private val statesByNode = mutable.HashMap.empty[Long, mutable.BitSet]
  private var q = 0
  private var maxDepth = 0
  private var lookups = 0L
  private var historyProbes = 0L
  private var acceptedEncounters = 0L

  override def foundNodesState(nodeId: Long, stateId: Int, nfaStateCount: Int): Unit = synchronized {
    q = math.max(q, nfaStateCount)
    statesByNode.getOrElseUpdate(nodeId, mutable.BitSet.empty).add(stateId)
    acceptedEncounters += 1
  }

  override def foundNodesLookup(
    location: LookupLocation,
    probes: Int,
    historyHitAge: Int,
    historyDepth: Int
  ): Unit = synchronized {
    lookups += 1
    historyProbes += probes
    maxDepth = math.max(maxDepth, historyDepth)
  }

  override def nextLevel(currentDepth: Int): Unit = synchronized {
    maxDepth = math.max(maxDepth, currentDepth)
  }

  override def finished(): Unit = synchronized {
    val counts = statesByNode.valuesIterator.map(_.size).toArray.sorted
    val n = counts.length
    val u = counts.iterator.map(_.toLong).sum
    def percentile(p: Double): Int =
      if (counts.isEmpty) 0 else counts(math.min(counts.length - 1, math.ceil(counts.length * p).toInt - 1))
    val rho = if (n == 0 || q == 0) 0.0 else u.toDouble / (n.toDouble * q)
    val json =
      s"{\"nfaStateCount\":$q,\"uniqueProductStates\":$u,\"distinctGraphNodes\":$n," +
        s"\"globalOccupancy\":$rho,\"medianStatesPerNode\":${percentile(0.5)}," +
        s"\"p95StatesPerNode\":${percentile(0.95)},\"maxStatesPerNode\":${counts.lastOption.getOrElse(0)}," +
        s"\"searchDepth\":$maxDepth,\"lookupCount\":$lookups,\"historyProbeCount\":$historyProbes," +
        s"\"acceptedProductStateEncounters\":$acceptedEncounters}\n"
    val path = Path.of(output)
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(
      path,
      json,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND
    )
    statesByNode.clear()
    q = 0
    maxDepth = 0
    lookups = 0L
    historyProbes = 0L
    acceptedEncounters = 0L
  }
}
