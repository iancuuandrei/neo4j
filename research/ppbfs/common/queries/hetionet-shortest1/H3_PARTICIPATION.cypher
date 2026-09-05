MATCH (s:Gene {hetioId: $source}), (t:Gene {hetioId: $target})
MATCH p = SHORTEST 1 (s)-[:G_P_BP|G_P_CC|G_P_MF|G_P_PW]->+(t)
RETURN length(p) AS pathLength
