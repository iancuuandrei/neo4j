MATCH (s:SnapNode {snapId: $source}), (t:SnapNode {snapId: $target})
MATCH p = SHORTEST 2 (s)-[:LINK]->+(t)
RETURN length(p) AS pathLength;
