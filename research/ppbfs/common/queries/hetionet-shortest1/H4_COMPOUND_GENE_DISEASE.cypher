MATCH (s:Compound {hetioId: $source}), (t:Disease {hetioId: $target})
MATCH p = SHORTEST 1 (s)-[:C_B_G|C_D_G|C_U_G|G_I_G|G_C_G|G_R_G|D_A_G|D_D_G|D_U_G]->+(t)
RETURN length(p) AS pathLength
