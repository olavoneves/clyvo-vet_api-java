-- ============================================================
-- MIGRATION V4 - Ticket medio da clinica
-- ============================================================
-- Base para os indicadores financeiros do dashboard.
-- DEFAULT 0 preenche as linhas existentes no mesmo comando,
-- permitindo o NOT NULL sem passo de backfill separado.
-- ============================================================
ALTER TABLE TB_CLV_CLINICA ADD (nr_ticket_medio NUMBER(10,2) DEFAULT 0 NOT NULL);
