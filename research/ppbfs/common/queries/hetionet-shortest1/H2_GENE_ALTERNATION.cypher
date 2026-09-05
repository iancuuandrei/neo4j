MATCH (s:Gene {hetioId: $source}), (t:Gene {hetioId: $target})
MATCH p = SHORTEST 1 (s)-[:G_I_G|G_C_G|G_R_G]->+(t)
RETURN length(p) AS pathLength
