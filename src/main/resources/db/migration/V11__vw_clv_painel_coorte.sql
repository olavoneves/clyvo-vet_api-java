-- ============================================================
-- CLYVO VET — V11 — Coorte tratado vs. controle
--
-- Uma linha por clínica e grupo, para o card de coorte do painel de receita.
--
-- POR QUE NÃO SERVE A VW_CLV_PAINEL_RECEITA QUE JÁ EXISTE
-- Aquela tem grão mensal e conta TODA obrigação no denominador, inclusive a que
-- ainda está PREVISTA. Uma obrigação que vence em outubro não é um não
-- comparecimento — ela nem teve a chance ainda. Contá-la afunda as duas taxas e,
-- pior, faz o número andar sozinho com o calendário: a mesma coorte "piora" a
-- cada mês que passa sem que nada tenha acontecido. Era isso que fazia o painel
-- mostrar 37% onde o seed sorteia 58%.
--
-- O DENOMINADOR AQUI SÃO AS OBRIGAÇÕES JÁ RESOLVIDAS — CUMPRIDA ou PERDIDA.
-- É a única leitura em que o percentual significa "de cada dez que chegaram ao
-- fim, quantas foram cumpridas". CANCELADA fica fora: cancelamento é decisão da
-- clínica, não desfecho do tutor, e somá-lo ao denominador puniria a clínica por
-- ter organizado a própria agenda.
--
-- O GRUPO VEM DA FLAG PERSISTIDA, nunca de FN_CLV_GRUPO_CONTROLE de novo. O
-- sorteio é determinístico, mas recalculá-lo na leitura criaria uma segunda
-- fonte de verdade que passa a divergir da primeira no dia em que os parâmetros
-- do sorteio mudarem — e aí ninguém sabe qual das duas o número da tela usou.
--
-- O TICKET MÉDIO SAI DAS CONSULTAS REALIZADAS DA PRÓPRIA CLÍNICA, e não de
-- TB_CLV_CLINICA.nr_ticket_medio: aquele é um valor declarado no cadastro, este
-- é o que a clínica de fato cobrou. Repetido nas duas linhas da clínica porque
-- é atributo dela, e não do grupo — quem consome lê de qualquer uma das duas.
--
-- Sem filtro de clínica: quem recorta é o serviço, a partir do TenantContext.
-- ============================================================

CREATE OR REPLACE VIEW VW_CLV_PAINEL_COORTE AS
SELECT
    o.id_clinica,
    CASE WHEN o.fl_grupo_controle = 'S' THEN 'CONTROLE' ELSE 'TRATADO' END
        AS ds_grupo,
    COUNT(*)                                                    AS qt_obrigacoes,
    SUM(CASE WHEN o.ds_status = 'CUMPRIDA' THEN 1 ELSE 0 END)   AS qt_cumpridas,
    ROUND(100 * SUM(CASE WHEN o.ds_status = 'CUMPRIDA' THEN 1 ELSE 0 END)
          / NULLIF(COUNT(*), 0), 2)                             AS pc_cumprimento,
    -- o tamanho da coorte em pets, e nao em obrigacoes: e ele que diz se o
    -- delta se sustenta. Dez obrigacoes de um pet so nao sao uma amostra.
    COUNT(DISTINCT o.id_pet)                                    AS qt_pets,
    -- a consulta nao carrega id_clinica: a clinica dela e a do pet atendido
    (SELECT ROUND(AVG(c.nr_valor), 2)
       FROM TB_CLV_CONSULTA c
       JOIN TB_CLV_PET p ON p.id_pet = c.id_pet
      WHERE p.id_clinica = o.id_clinica
        AND c.nr_valor IS NOT NULL
        AND c.ds_status = 'REALIZADA')                          AS vl_ticket_medio
  FROM TB_CLV_OBRIGACAO o
 WHERE o.ds_status IN ('CUMPRIDA', 'PERDIDA')
 GROUP BY o.id_clinica,
          CASE WHEN o.fl_grupo_controle = 'S' THEN 'CONTROLE' ELSE 'TRATADO' END;
