MATCH (s:Compound {hetioId: $source}), (t:Disease {hetioId: $target})
MATCH p = SHORTEST 1 (s)-[:A_D_G|A_E_G|A_U_G|C_B_G|C_C_SE|C_D_G|C_P_D|C_R_C|C_T_D|C_U_G|D_A_G|D_D_G|D_L_A|D_P_S|D_R_D|D_U_G|G_C_G|G_I_G|G_P_BP|G_P_CC|G_P_MF|G_P_PW|G_R_G|PC_I_C]->+(t)
RETURN length(p) AS pathLength
