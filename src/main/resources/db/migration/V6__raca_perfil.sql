-- ============================================================
-- MIGRATION V6 - Perfil clinico da raca
-- ============================================================
-- Dados de referencia usados pelo motor de protocolo em PL/SQL:
-- grupo, porte padrao, braquicefalia e predisposicoes da raca
-- alimentam a sugestao de conduta antes mesmo da anamnese.
-- Todas nullable: o catalogo de racas sera enriquecido aos poucos.
-- ============================================================
ALTER TABLE TB_CLV_RACA ADD (
    ds_grupo_raca     VARCHAR2(60),
    ds_porte_padrao   VARCHAR2(10),
    fl_braquicefalico CHAR(1),
    ds_predisposicoes VARCHAR2(1000)
);

ALTER TABLE TB_CLV_RACA
    ADD CONSTRAINT chk_raca_porte_padrao
    CHECK (ds_porte_padrao IN ('MINI','PEQUENO','MEDIO','GRANDE','GIGANTE'));

ALTER TABLE TB_CLV_RACA
    ADD CONSTRAINT chk_raca_braquicefalico
    CHECK (fl_braquicefalico IN ('S','N'));
