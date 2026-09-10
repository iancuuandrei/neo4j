-- P7 materiality: canonical unbound WALK server query template.
--
-- Binds the SOURCE only; the end node (t) is UNBOUND, so this goes through
-- StatefulShortestPath(All, Walk), not the both-endpoints-bound SHORTEST 1 path.
-- Requires the Cypher 25 parser (REPEATABLE ELEMENTS is rejected in Cypher 5).
-- The {1,12} upper bound is required: unbounded * is rejected under WALK
-- (potentially infinite rows).
--
-- Parameters: $source (snapId of the source node), $limit (row cap).
-- Timed instances: $limit 75 (source 2022306), $limit 500 (sources 3705591,
-- 1042128), $limit 50 (source 2022306).

CYPHER 25
MATCH REPEATABLE ELEMENTS p = SHORTEST 1 (s:SnapNode {snapId: $source})((a)-[:LINK]->(b)){1,12}(t)
RETURN p LIMIT $limit
