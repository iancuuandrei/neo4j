MATCH (s:Gene {hetioId: $source}), (t:Gene {hetioId: $target})
MATCH p = SHORTEST 2 (s)-[:G_I_G]->+(t)
RETURN length(p) AS pathLength
