-- ============================================================
-- MIGRATION V5 - Valor da consulta
-- ============================================================
-- Nullable: consulta agendada ainda nao tem valor fechado, e
-- atendimento de cortesia nunca tera.
-- ============================================================
ALTER TABLE TB_CLV_CONSULTA ADD (nr_valor NUMBER(10,2));
