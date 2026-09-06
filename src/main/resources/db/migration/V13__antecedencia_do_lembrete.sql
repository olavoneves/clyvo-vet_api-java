-- ============================================================
-- CLYVO VET — V13 — Antecedência do lembrete, no catálogo
--
-- Quantos dias antes do vencimento o tutor é avisado é REGRA CLÍNICA, e regra
-- clínica mora no catálogo de protocolos. Uma constante em Java — ou pior, um
-- if por categoria dentro do job — faria a clínica depender de um deploy para
-- decidir que vermífugo avisa com 5 dias e cirurgia com 15. O motor já lê tudo
-- o mais daqui (offset, janela, valor de referência); a antecedência não tinha
-- razão para ser a exceção.
--
-- POR QUE NO PROTOCOLO E NÃO NA ETAPA
-- A janela de execução (nr_janela_antes / nr_janela_depois) é por etapa, porque
-- é clínica: a segunda dose tem tolerância diferente da primeira. Já o quanto
-- antes se avisa é do protocolo inteiro — é sobre o comportamento do tutor
-- diante daquele tipo de cuidado, não sobre a etapa. Colocar por etapa daria
-- granularidade que ninguém preencheria, e todas as linhas ficariam iguais.
--
-- O DEFAULT DA COLUNA É 7. Protocolo novo cadastrado sem informar nada avisa com
-- uma semana, que é o comportamento menos surpreendente. O UPDATE abaixo ajusta
-- as linhas que já existem por categoria — abaixo, o porquê de cada número.
-- ============================================================

ALTER TABLE TB_CLV_PROTOCOLO ADD (
    nr_antecedencia_lembrete_dias NUMBER(3) DEFAULT 7 NOT NULL
);

-- Antecedência não é negativa (avisar depois do vencimento não é antecedência,
-- é cobrança, e essa já acontece porque a obrigação vencida continua PREVISTA)
-- e não passa de 90 dias, que é mais do que qualquer horizonte de geração.
ALTER TABLE TB_CLV_PROTOCOLO ADD CONSTRAINT ck_clv_prot_antecedencia
    CHECK (nr_antecedencia_lembrete_dias BETWEEN 0 AND 90);

COMMENT ON COLUMN TB_CLV_PROTOCOLO.nr_antecedencia_lembrete_dias IS
    'Dias antes de dt_prevista em que a varredura leva a obrigacao a NOTIFICADA';

-- ============================================================
-- Padrão por categoria
--
-- O critério é quanto tempo o tutor precisa para se organizar, e não a
-- importância clínica do procedimento:
--
--   CIRURGIA (15) — exige jejum, acompanhante e um dia livre. Avisar com uma
--                   semana é avisar tarde para quem trabalha.
--   CHECKUP  (14) — não tem urgência, então concorre com tudo na agenda do
--                   tutor; quanto mais cedo entra, mais chance de acontecer.
--   ODONTO   (10) — anestesia e meio período na clínica.
--   VACINA    (7) — data marcada e conhecida; uma semana basta e evita que o
--                   tutor esqueça de novo até lá.
--   EXAME     (7) — alguns pedem preparo, uma semana cobre.
--   VERMIFUGO (5) — dose única, resolvida em qualquer visita curta.
--   RETORNO   (3) — o intervalo entre a consulta e o retorno costuma ser curto;
--                   avisar com 7 dias cairia antes da própria consulta.
--   MONITORAMENTO (3) — recorrente e de intervalo curto pela natureza; uma
--                   antecedência longa faria o aviso da próxima ocorrência sair
--                   antes de a anterior ter sido cumprida.
-- ============================================================
UPDATE TB_CLV_PROTOCOLO SET nr_antecedencia_lembrete_dias =
    CASE ds_categoria
        WHEN 'CIRURGIA'      THEN 15
        WHEN 'CHECKUP'       THEN 14
        WHEN 'ODONTO'        THEN 10
        WHEN 'VACINA'        THEN 7
        WHEN 'EXAME'         THEN 7
        WHEN 'VERMIFUGO'     THEN 5
        WHEN 'RETORNO'       THEN 3
        WHEN 'MONITORAMENTO' THEN 3
        ELSE 7
    END;

-- A varredura filtra por status, grupo e vencimento, nesta ordem de seletividade.
-- Sem o índice ela varre TB_CLV_OBRIGACAO inteira a cada execução — barato hoje,
-- caro no primeiro ambiente com histórico de verdade.
CREATE INDEX ix_clv_obrig_varredura
    ON TB_CLV_OBRIGACAO (ds_status, fl_grupo_controle, dt_prevista);

COMMIT;
