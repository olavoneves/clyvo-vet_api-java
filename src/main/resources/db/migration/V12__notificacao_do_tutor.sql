-- ============================================================
-- CLYVO VET — V12 — Notificação do tutor
--
-- O elo "lembrete" da cadeia de valor estava vazio: a obrigação transitava para
-- NOTIFICADA e nada chegava a lugar nenhum. A transição registrava que um aviso
-- deveria ter saído, e ninguém guardava se saiu, para quem, por onde.
--
-- POR QUE UMA TABELA E NÃO O OUTBOX QUE JÁ EXISTE
-- TB_CLV_OUTBOX_EVENT é fila de integração: linha efêmera, consumida e marcada
-- como publicada, com payload livre. Notificação é o contrário — é estado que o
-- tutor consulta, relê e marca como lida, e que precisa de leitura indexada por
-- destinatário. Enfiar caixa de entrada no outbox transformaria a fila em
-- tabela de consulta e faria o expurgo dela apagar a caixa do tutor.
--
-- O CANAL É COLUNA, E NÃO TABELA POR CANAL. Hoje só existe APP. Push, WhatsApp e
-- e-mail entram como valores novos aqui e como implementações novas da porta em
-- Java, sem tocar no motor nem nesta tabela.
--
-- dt_leitura NULA É O ESTADO "NÃO LIDA", em vez de uma flag separada: dois
-- campos para o mesmo fato divergem no dia em que alguém escrever um sem o
-- outro, e a data é o que a tela quer mostrar de qualquer forma.
--
-- A obrigação é opcional de propósito: nem toda notificação futura nasce de uma
-- obrigação (confirmação de agendamento, aviso da clínica). O tutor é que é
-- obrigatório — notificação sem destinatário não é notificação.
-- ============================================================

CREATE TABLE TB_CLV_NOTIFICACAO (
    id_notificacao   NUMBER(19)     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_clinica       NUMBER(19)     NOT NULL,
    id_tutor         NUMBER(19)     NOT NULL,
    id_obrigacao     NUMBER(19),
    ds_canal         VARCHAR2(15)   DEFAULT 'APP' NOT NULL,
    ds_titulo        VARCHAR2(120)  NOT NULL,
    ds_mensagem      VARCHAR2(500)  NOT NULL,
    dt_emissao       TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
    dt_leitura       TIMESTAMP,
    CONSTRAINT fk_clv_notif_clinica   FOREIGN KEY (id_clinica)
        REFERENCES TB_CLV_CLINICA (id_clinica),
    CONSTRAINT fk_clv_notif_tutor     FOREIGN KEY (id_tutor)
        REFERENCES TB_CLV_TUTOR (id_tutor),
    CONSTRAINT fk_clv_notif_obrigacao FOREIGN KEY (id_obrigacao)
        REFERENCES TB_CLV_OBRIGACAO (id_obrigacao),
    CONSTRAINT ck_clv_notif_canal
        CHECK (ds_canal IN ('APP', 'PUSH', 'WHATSAPP', 'EMAIL'))
);

-- A consulta da caixa de entrada, exatamente como a tela a faz: as do tutor, não
-- lidas primeiro, mais recentes antes. O índice cobre o filtro e a ordem.
CREATE INDEX ix_clv_notif_caixa
    ON TB_CLV_NOTIFICACAO (id_tutor, dt_leitura, dt_emissao DESC);

-- Uma obrigação avisa uma vez por canal. A transição para NOTIFICADA pode ser
-- repetida — reprocessamento, correção manual, o motor rodando de novo — e sem
-- esta restrição cada repetição empilharia um lembrete duplicado na caixa do
-- tutor. Nulos não colidem no Oracle, então notificação sem obrigação fica livre.
CREATE UNIQUE INDEX ux_clv_notif_obrigacao_canal
    ON TB_CLV_NOTIFICACAO (id_obrigacao, ds_canal);

COMMENT ON TABLE  TB_CLV_NOTIFICACAO IS
    'Lembretes entregues ao tutor. Uma linha por obrigacao e canal.';
COMMENT ON COLUMN TB_CLV_NOTIFICACAO.dt_leitura IS
    'Nula enquanto nao lida; e o proprio estado, nao ha flag separada.';
