MATCH (s:GMarkNode {gmarkId: $source}), (t:GMarkNode {gmarkId: $target})
MATCH p = SHORTEST 2 (s)-[:P0|P1|P2|P3]->+(t)
RETURN length(p) AS pathLength
