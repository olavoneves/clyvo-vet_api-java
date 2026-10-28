-- ============================================================
-- CLYVO VET — V16 — Detalhe da obrigação e LIMPAR do seed
--
-- Duas mudanças independentes, na mesma migration porque vão juntas para a
-- FIAP no mesmo deploy:
--
--   1. VW_CLV_OBRIGACAO_DETALHE — uma linha por obrigação, já com pet, tutor,
--      protocolo e etapa por nome, para o clyvo-insights (.NET) paginar.
--   2. PKG_CLV_SEED.LIMPAR — passa a apagar as tabelas-filhas que ele não
--      conhecia, e que o faziam falhar com ORA-02292.
--
-- O CONTRATO DE COLUNAS DA VIEW É FIXO. O .NET é escrito contra ele: renomear,
-- reordenar ou acrescentar coluna aqui é mudança de contrato e tem que ser
-- combinada com quem consome, não feita numa migration de passagem.
--
-- NENHUM DADO DE CONTATO DO TUTOR. Só o nome. O .NET não manda lembrete; quem
-- manda é o core, e e-mail e telefone não precisam sair dele para outro
-- serviço só porque estavam a um join de distância.
--
-- O GRUPO VEM DA FLAG PERSISTIDA, nunca de PKG_CLV_MOTOR.GRUPO_CONTROLE — pelo
-- mesmo motivo da V11: recalcular o sorteio na leitura criaria uma segunda
-- fonte de verdade. E a view continua lendo só tabelas, como as outras duas:
-- uma view que depende de pacote fica INVALID toda vez que o pacote recompila.
--
-- INNER JOIN em tudo, porque todos os caminhos são NOT NULL (id_pet,
-- id_versao_protocolo e id_etapa na obrigação, id_tutor no pet, id_protocolo
-- na versão). Mas NOT NULL não garante que a linha do outro lado exista: na
-- FIAP o baseline é a V8 e as FKs da V0_1 nunca rodaram por migration. Uma
-- obrigação órfã sumiria da view em silêncio e o .NET paginaria um total
-- menor que o real. Por isso a checagem 2 compara as contagens e aborta.
--
-- Sem índice novo: o filtro natural de quem pagina (clínica, status, data
-- prevista) já está em IX_OBRIGACAO_TENANT.
--
-- Ordem: 1 view → 2 contagem → 3 body do PKG_CLV_SEED → 4 conferência final.
-- Nenhuma rotina de seed é executada aqui.
-- ============================================================


-- ------------------------------------------------------------
-- 1. VW_CLV_OBRIGACAO_DETALHE
-- ------------------------------------------------------------
-- Sem filtro de clínica: quem recorta é quem consome, pelo ID_CLINICA.
CREATE OR REPLACE VIEW VW_CLV_OBRIGACAO_DETALHE AS
SELECT
    o.id_obrigacao,
    o.id_clinica,
    o.id_pet,
    p.nm_pet,
    t.nm_tutor,
    pr.nm_protocolo,
    pr.ds_categoria,
    e.nm_etapa,
    o.ds_status,
    o.fl_grupo_controle,
    o.dt_prevista,
    o.dt_janela_inicio,
    o.dt_janela_fim,
    o.nr_valor_estimado,
    o.nr_valor_realizado
  FROM TB_CLV_OBRIGACAO o
  JOIN TB_CLV_PET p               ON p.id_pet               = o.id_pet
  JOIN TB_CLV_TUTOR t             ON t.id_tutor             = p.id_tutor
  JOIN TB_CLV_VERSAO_PROTOCOLO vp ON vp.id_versao_protocolo = o.id_versao_protocolo
  JOIN TB_CLV_PROTOCOLO pr        ON pr.id_protocolo        = vp.id_protocolo
  JOIN TB_CLV_ETAPA_PROTOCOLO e   ON e.id_etapa             = o.id_etapa;


-- ------------------------------------------------------------
-- 2. A view vê todas as obrigações?
-- ------------------------------------------------------------
-- Se não vê, há obrigação apontando para pet, tutor, versão, protocolo ou
-- etapa que não existe. Corrigir o dado, não afrouxar o join.
DECLARE
    v_tabela NUMBER;
    v_view   NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_tabela FROM TB_CLV_OBRIGACAO;
    SELECT COUNT(*) INTO v_view   FROM VW_CLV_OBRIGACAO_DETALHE;

    IF v_tabela <> v_view THEN
        RAISE_APPLICATION_ERROR(-20903,
            'V16: VW_CLV_OBRIGACAO_DETALHE tem ' || v_view || ' linhas e '
            || 'TB_CLV_OBRIGACAO tem ' || v_tabela || '. Há obrigação órfã: '
            || 'algum id_pet, id_tutor, id_versao_protocolo, id_protocolo ou '
            || 'id_etapa sem a linha correspondente.');
    END IF;
END;
/


-- ------------------------------------------------------------
-- 3. PKG_CLV_SEED: body da V15 com o LIMPAR corrigido
-- ------------------------------------------------------------
-- A spec não muda. O resto do body é cópia literal da V15.
--
-- O LIMPAR não conhecia cinco tabelas-filhas do que ele apaga:
--   TB_CLV_NOTIFICACAO       (V12)  → obrigação, tutor, clínica
--   TB_CLV_APLICACAO_VACINA  (V0)   → consulta, pet, veterinário
--   TB_CLV_ALERTA_IOT        (V0)   → leitura e consulta
--   TB_CLV_LEITURA_IOT       (V0)   → sensor
--   TB_CLV_SENSOR_IOT        (V0)   → pet
-- A da V12 é a que derrubava o LIMPAR em toda base que já tinha mandado um
-- lembrete. As da V0 estão vazias hoje, mas a API grava nelas: o primeiro
-- registro de vacina ou de sensor faria o LIMPAR falhar do mesmo jeito.
-- Mesmo critério do resto do LIMPAR: a tabela inteira, sem recorte.
--
-- TB_CLV_PROTOCOLO também referencia a clínica, mas é catálogo, não base de
-- demonstração: o seed não o recria, e apagá-lo levaria versões, etapas e
-- regras junto. Nenhum protocolo tem clínica hoje e a API não cria protocolo.

-- Os membros vêm em ordem de dependência, sem declaração antecipada: o
-- PainelCoorteIntegracaoTest lê as taxas-alvo de DESFECHOS no USER_SOURCE
-- deste body e espera achar cada uma exatamente uma vez.
CREATE OR REPLACE PACKAGE BODY PKG_CLV_SEED AS

    PROCEDURE LIMPAR IS
    BEGIN
        -- filhas que o LIMPAR da V15 nao conhecia; ver o cabecalho da V16
        DELETE FROM TB_CLV_NOTIFICACAO;
        DELETE FROM TB_CLV_ALERTA_IOT;
        DELETE FROM TB_CLV_LEITURA_IOT;
        DELETE FROM TB_CLV_SENSOR_IOT;
        DELETE FROM TB_CLV_APLICACAO_VACINA;
        DELETE FROM TB_CLV_AUDIT_OBRIGACAO;
        DELETE FROM TB_CLV_OBRIGACAO_TRANSICAO;
        DELETE FROM TB_CLV_OUTBOX_EVENT;
        DELETE FROM TB_CLV_OBRIGACAO;
        DELETE FROM TB_CLV_AGENDAMENTO;
        DELETE FROM TB_CLV_CONDICAO_PET;
        DELETE FROM TB_CLV_ALERGIA_PET;
        DELETE FROM TB_CLV_PRESCRICAO;
        DELETE FROM TB_CLV_EXAME;
        DELETE FROM TB_CLV_ANAMNESE;
        DELETE FROM TB_CLV_CONSULTA;
        DELETE FROM TB_CLV_PET;
        DELETE FROM TB_CLV_TUTOR;
        DELETE FROM TB_CLV_COLABORADOR;
        DELETE FROM TB_CLV_VETERINARIO;
        DELETE FROM TB_CLV_CLINICA;
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.LIMPAR', SQLCODE, SQLERRM);
            RAISE;
    END LIMPAR;

    PROCEDURE BASE IS
        v_a NUMBER(19);
        v_b NUMBER(19);
    BEGIN
        INSERT INTO TB_CLV_CLINICA (nm_clinica, ds_cnpj, ds_logradouro, ds_numero,
                                    ds_bairro, nm_cidade, sg_estado, nr_cep,
                                    nr_telefone, nr_ticket_medio)
        VALUES ('Clinica Vida Animal', '11222333000181', 'Av. Paulista', '1500',
                'Bela Vista', 'Sao Paulo', 'SP', '01310100', '1133334444', 420)
        RETURNING id_clinica INTO v_a;

        INSERT INTO TB_CLV_CLINICA (nm_clinica, ds_cnpj, ds_logradouro, ds_numero,
                                    ds_bairro, nm_cidade, sg_estado, nr_cep,
                                    nr_telefone, nr_ticket_medio)
        VALUES ('PetCare Zona Sul', '44555666000172', 'Av. Santo Amaro', '820',
                'Brooklin', 'Sao Paulo', 'SP', '04505000', '1155556666', 380)
        RETURNING id_clinica INTO v_b;

        INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                        ds_email, ds_senha_hash, id_clinica)
        VALUES ('Dra. Helena Prado', 'SP-11001', 'Clinica geral',
                'helena@vidaanimal.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                v_a);

        INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                        ds_email, ds_senha_hash, id_clinica)
        VALUES ('Dr. Rafael Nunes', 'SP-11002', 'Nefrologia',
                'rafael@vidaanimal.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                v_a);

        INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                        ds_email, ds_senha_hash, id_clinica)
        VALUES ('Dra. Marina Costa', 'SP-22001', 'Clinica geral',
                'marina@petcare.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                v_b);

        INSERT INTO TB_CLV_COLABORADOR (nm_colaborador, ds_email, ds_senha_hash,
                                        ds_cargo, id_clinica)
        VALUES ('Patricia Moraes', 'patricia@vidaanimal.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'ADMINISTRADOR', v_a);

        INSERT INTO TB_CLV_COLABORADOR (nm_colaborador, ds_email, ds_senha_hash,
                                        ds_cargo, id_clinica)
        VALUES ('Diego Tavares', 'diego@petcare.com.br',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'RECEPCAO', v_b);

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.BASE', SQLCODE, SQLERRM);
            RAISE;
    END BASE;

    PROCEDURE GERAR (
        p_clinica   IN NUMBER,
        p_pet       IN NUMBER,
        p_consulta  IN NUMBER,
        p_data      IN DATE,
        p_horizonte IN NUMBER DEFAULT 550
    ) IS
        v_qtd NUMBER;
    BEGIN
        PKG_CLV_MOTOR.GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
            'CONSULTA_REGISTRADA', NULL, p_data, NULL, p_horizonte, v_qtd);

        PKG_CLV_MOTOR.GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
            'PESO_ATUALIZADO', NULL, p_data, NULL, p_horizonte, v_qtd);

        IF PKG_CLV_MOTOR.FASE_VIDA(p_pet, p_data) = 'SENIOR' THEN
            PKG_CLV_MOTOR.GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
                'FASE_VIDA_ALTERADA', NULL, p_data, NULL, p_horizonte, v_qtd);
        END IF;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.GERAR', SQLCODE,
                            'pet=' || p_pet || ' consulta=' || p_consulta || ' ' || SQLERRM);
            RAISE;
    END GERAR;

    FUNCTION SEED_CONSULTA (
        p_clinica     IN NUMBER,
        p_pet         IN NUMBER,
        p_vet         IN NUMBER,
        p_data        IN DATE,
        p_motivo      IN VARCHAR2,
        p_peso        IN NUMBER,
        p_valor       IN NUMBER   DEFAULT NULL,
        p_diagnostico IN VARCHAR2 DEFAULT NULL,
        p_gerar       IN CHAR     DEFAULT 'S'
    ) RETURN NUMBER IS
        v_consulta NUMBER(19);
        v_cc       VARCHAR2(12);
        v_sorteio  NUMBER;
    BEGIN
        INSERT INTO TB_CLV_CONSULTA (dt_consulta, ds_motivo, ds_diagnostico,
                                     ds_status, id_pet, id_veterinario, nr_valor)
        VALUES (p_data, p_motivo, p_diagnostico, 'REALIZADA', p_pet, p_vet,
                NVL(p_valor, 180))
        RETURNING id_consulta INTO v_consulta;

        v_sorteio := DBMS_RANDOM.VALUE(0,1);
        v_cc := CASE WHEN v_sorteio < 0.70 THEN 'IDEAL'
                     WHEN v_sorteio < 0.85 THEN 'MAGRO'
                     ELSE 'SOBREPESO' END;

        INSERT INTO TB_CLV_ANAMNESE (id_consulta, ds_queixa_principal, nr_peso_kg,
                                     nr_temperatura_c, nr_freq_cardiaca,
                                     nr_freq_respiratoria, ds_condicao_corporal)
        VALUES (v_consulta, p_motivo, p_peso,
                ROUND(38 + DBMS_RANDOM.VALUE(0, 1.4), 1),
                ROUND(DBMS_RANDOM.VALUE(70, 140)),
                ROUND(DBMS_RANDOM.VALUE(15, 35)),
                v_cc);

        IF p_gerar = 'S' THEN
            GERAR(p_clinica, p_pet, v_consulta, p_data);
        END IF;

        RETURN v_consulta;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.SEED_CONSULTA', SQLCODE,
                            'pet=' || p_pet || ' ' || SQLERRM);
            RAISE;
    END SEED_CONSULTA;

    PROCEDURE POPULACAO (
        p_qtd   IN NUMBER DEFAULT 400,
        p_meses IN NUMBER DEFAULT 18
    ) IS
        TYPE t_txt IS TABLE OF VARCHAR2(60);

        v_nomes  t_txt := t_txt('Luna','Mel','Bidu','Cacau','Amora','Bob','Nina','Zeus',
                                'Frida','Simba','Maia','Pipoca','Rex','Lola','Toby','Bela');
        v_sobre  t_txt := t_txt('Silva','Souza','Oliveira','Santos','Lima','Costa',
                                'Rocha','Martins','Barbosa','Azevedo');
        v_motivo t_txt := t_txt('Consulta de rotina','Vacinacao','Retorno',
                                'Avaliacao clinica','Queixa gastrointestinal',
                                'Avaliacao dermatologica');

        v_clinica_a NUMBER(19);
        v_clinica_b NUMBER(19);
        v_clinica   NUMBER(19);
        v_tutor     NUMBER(19);
        v_pet       NUMBER(19);
        v_vet       NUMBER(19);
        v_raca      NUMBER(19);
        v_porte     VARCHAR2(10);
        v_nasc      DATE;
        v_data      DATE;
        v_peso      NUMBER(7,2);
        v_qtd_c     NUMBER;
        v_cons      NUMBER(19);
    BEGIN
        DBMS_RANDOM.SEED(20260909);   -- reprodutível

        SELECT MIN(id_clinica), MAX(id_clinica)
          INTO v_clinica_a, v_clinica_b FROM TB_CLV_CLINICA;

        FOR i IN 1 .. p_qtd LOOP

            v_clinica := CASE WHEN DBMS_RANDOM.VALUE(0,1) < 0.6
                              THEN v_clinica_a ELSE v_clinica_b END;

            SELECT MIN(id_veterinario) INTO v_vet
              FROM TB_CLV_VETERINARIO WHERE id_clinica = v_clinica;

            -- Raça aleatória; o porte padrão da raça vira o porte do pet.
            SELECT id_raca, NVL(ds_porte_padrao,'MEDIO')
              INTO v_raca, v_porte
              FROM (SELECT id_raca, ds_porte_padrao
                      FROM TB_CLV_RACA
                     WHERE ds_porte_padrao IS NOT NULL
                     ORDER BY DBMS_RANDOM.VALUE)
             WHERE ROWNUM = 1;

            -- Idade entre 2 meses e 12 anos
            v_nasc := TRUNC(SYSDATE) - ROUND(DBMS_RANDOM.VALUE(60, 4380));

            INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                                      ds_canal_preferencial, dt_cadastro, id_clinica)
            VALUES (v_sobre(MOD(i,10)+1) || ' ' || v_sobre(MOD(i*3,10)+1),
                    'tutor' || i || '@exemplo.com',
                    '119' || LPAD(i, 8, '0'),
                    '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                    CASE WHEN MOD(i,4) = 0 THEN 'WEB' ELSE 'APP' END,
                    TRUNC(SYSDATE) - ROUND(DBMS_RANDOM.VALUE(30, 900)),
                    v_clinica)
            RETURNING id_tutor INTO v_tutor;

            INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                                    ds_porte, fl_castrado, ds_status_pet,
                                    id_tutor, id_raca, id_clinica)
            VALUES (v_nomes(MOD(i,16)+1), v_nasc,
                    CASE WHEN MOD(i,2) = 0 THEN 'M' ELSE 'F' END,
                    'CHIP' || LPAD(i+1000, 12, '0'),
                    v_porte,
                    CASE WHEN DBMS_RANDOM.VALUE(0,1) < 0.55 THEN 'S' ELSE 'N' END,
                    'ATIVO', v_tutor, v_raca, v_clinica)
            RETURNING id_pet INTO v_pet;

            -- Peso base coerente com o porte
            v_peso := CASE v_porte
                          WHEN 'MINI'    THEN DBMS_RANDOM.VALUE(2, 5)
                          WHEN 'PEQUENO' THEN DBMS_RANDOM.VALUE(5, 10)
                          WHEN 'MEDIO'   THEN DBMS_RANDOM.VALUE(10, 25)
                          WHEN 'GRANDE'  THEN DBMS_RANDOM.VALUE(25, 40)
                          ELSE                DBMS_RANDOM.VALUE(40, 65)
                      END;

            -- 2 a 6 consultas na janela histórica, sempre após o nascimento
            v_qtd_c := 2 + ROUND(DBMS_RANDOM.VALUE(0, 4));
            FOR j IN 1 .. v_qtd_c LOOP
                v_data := TRUNC(SYSDATE) - ROUND(DBMS_RANDOM.VALUE(5, p_meses*30));
                IF v_data > v_nasc + 30 THEN
                    v_cons := SEED_CONSULTA(
                        v_clinica, v_pet, v_vet, v_data,
                        v_motivo(MOD(i+j,6)+1),
                        ROUND(v_peso * DBMS_RANDOM.VALUE(0.92, 1.08), 2),
                        ROUND(DBMS_RANDOM.VALUE(120, 520)));
                END IF;
            END LOOP;

            IF MOD(i, 50) = 0 THEN
                COMMIT;
            END IF;
        END LOOP;

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.POPULACAO', SQLCODE, SQLERRM);
            RAISE;
    END POPULACAO;

    PROCEDURE HEROIS IS
        v_clinica NUMBER(19);
        v_vet     NUMBER(19);
        v_vet_nf  NUMBER(19);
        v_tutor   NUMBER(19);
        v_pet     NUMBER(19);
        v_raca    NUMBER(19);
        v_cons    NUMBER(19);
        v_cond    NUMBER(19);
        v_qtd     NUMBER;
    BEGIN
        SELECT MIN(id_clinica) INTO v_clinica FROM TB_CLV_CLINICA;
        SELECT MIN(id_veterinario), MAX(id_veterinario) INTO v_vet, v_vet_nf
          FROM TB_CLV_VETERINARIO WHERE id_clinica = v_clinica;

        -- ---------- THOR · Golden Retriever, ~3 meses ----------
        INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                                  ds_canal_preferencial, dt_cadastro, id_clinica)
        VALUES ('Camila Ferreira', 'camila.ferreira@exemplo.com', '11987650001',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'APP', SYSDATE-70, v_clinica)
        RETURNING id_tutor INTO v_tutor;

        SELECT id_raca INTO v_raca FROM TB_CLV_RACA WHERE nm_raca = 'Golden Retriever';

        INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                                ds_pelagem, ds_porte, fl_castrado, ds_status_pet,
                                id_tutor, id_raca, id_clinica)
        VALUES ('Thor', TRUNC(SYSDATE)-95, 'M', 'CHIP900000000001',
                'Dourada', 'GRANDE', 'N', 'ATIVO', v_tutor, v_raca, v_clinica)
        RETURNING id_pet INTO v_pet;

        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet, TRUNC(SYSDATE)-50,
                                'Primeira consulta de filhote', 6.2, 180);
        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet, TRUNC(SYSDATE)-20,
                                'Retorno e segunda dose', 9.8, 120);

        -- ---------- NALA · Persa, ~8 anos, DRC ----------
        INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                                  ds_canal_preferencial, dt_cadastro, id_clinica)
        VALUES ('Roberto Almeida', 'roberto.almeida@exemplo.com', '11987650002',
                '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW',
                'APP', SYSDATE-700, v_clinica)
        RETURNING id_tutor INTO v_tutor;

        SELECT id_raca INTO v_raca FROM TB_CLV_RACA WHERE nm_raca = 'Persa';

        INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                                ds_pelagem, ds_porte, fl_castrado, ds_status_pet,
                                id_tutor, id_raca, id_clinica)
        VALUES ('Nala', ADD_MONTHS(TRUNC(SYSDATE), -98), 'F', 'CHIP900000000002',
                'Creme', 'PEQUENO', 'S', 'EM_TRATAMENTO', v_tutor, v_raca, v_clinica)
        RETURNING id_pet INTO v_pet;

        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet,
                                ADD_MONTHS(TRUNC(SYSDATE),-16),
                                'Check-up anual', 4.1, 260);
        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet,
                                ADD_MONTHS(TRUNC(SYSDATE),-10),
                                'Apatia e perda de peso', 3.6, 320);
        v_cons := SEED_CONSULTA(v_clinica, v_pet, v_vet_nf,
                                ADD_MONTHS(TRUNC(SYSDATE),-6),
                                'Investigacao renal', 3.4, 420,
                                'Doenca renal cronica estagio 2');

        -- Diagnóstico crônico ativa o protocolo de monitoramento renal.
        SELECT id_tipo_condicao INTO v_cond
          FROM TB_CLV_TIPO_CONDICAO WHERE nm_condicao = 'Doenca renal cronica';

        INSERT INTO TB_CLV_CONDICAO_PET (id_pet, id_tipo_condicao, dt_diagnostico,
                                         ds_observacao, fl_ativo, id_consulta_origem)
        VALUES (v_pet, v_cond, ADD_MONTHS(TRUNC(SYSDATE),-6),
                'Estagio 2 IRIS. Dieta renal iniciada.', 'S', v_cons);

        PKG_CLV_MOTOR.GERAR_OBRIGACOES(v_clinica, v_pet, v_cons,
            'CONDICAO_DIAGNOSTICADA', NULL,
            ADD_MONTHS(TRUNC(SYSDATE),-6), NULL, 400, v_qtd);

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.HEROIS', SQLCODE, SQLERRM);
            RAISE;
    END HEROIS;

    PROCEDURE DESFECHOS (
        p_taxa_tratado  IN NUMBER DEFAULT 0.58,
        p_taxa_controle IN NUMBER DEFAULT 0.36
    ) IS
        v_taxa    NUMBER;
        v_sorteio NUMBER;
        v_cons    NUMBER(19);
        v_agend   NUMBER(19);
        v_vet     NUMBER(19);
        v_i       NUMBER := 0;
    BEGIN
        DBMS_RANDOM.SEED(20260910);

        FOR o IN (SELECT o.id_obrigacao, o.id_clinica, o.id_pet, o.fl_grupo_controle,
                         o.dt_prevista, o.nr_valor_estimado, e.nm_etapa
                    FROM TB_CLV_OBRIGACAO o
                    JOIN TB_CLV_ETAPA_PROTOCOLO e ON e.id_etapa = o.id_etapa
                   WHERE o.ds_status = 'PREVISTA'
                   ORDER BY o.dt_prevista) LOOP

            SELECT MIN(id_veterinario) INTO v_vet
              FROM TB_CLV_VETERINARIO WHERE id_clinica = o.id_clinica;

            IF o.dt_prevista < TRUNC(SYSDATE) THEN

                IF o.fl_grupo_controle = 'N' THEN
                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'NOTIFICADA',
                        'Lembrete enviado', 'SEED');
                    v_taxa := p_taxa_tratado;
                ELSE
                    v_taxa := p_taxa_controle;
                END IF;

                v_sorteio := DBMS_RANDOM.VALUE(0,1);

                IF v_sorteio < v_taxa THEN
                    INSERT INTO TB_CLV_AGENDAMENTO (dt_agendamento, hr_agendamento,
                            ds_status, ds_canal_origem, id_pet, id_veterinario)
                    VALUES (o.dt_prevista, '09:00', 'REALIZADO',
                            CASE WHEN o.fl_grupo_controle = 'N' THEN 'APP' ELSE 'WEB' END,
                            o.id_pet, v_vet)
                    RETURNING id_agendamento INTO v_agend;

                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'AGENDADA',
                        'Tutor agendou', 'SEED', NULL, v_agend);

                    INSERT INTO TB_CLV_CONSULTA (dt_consulta, ds_motivo, ds_status,
                                                 id_pet, id_veterinario, nr_valor)
                    VALUES (o.dt_prevista, SUBSTR(o.nm_etapa,1,500), 'REALIZADA',
                            o.id_pet, v_vet, o.nr_valor_estimado)
                    RETURNING id_consulta INTO v_cons;

                    UPDATE TB_CLV_AGENDAMENTO SET id_consulta = v_cons
                     WHERE id_agendamento = v_agend;

                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'CUMPRIDA',
                        'Comparecimento confirmado', 'SEED', NULL, NULL, v_cons,
                        o.nr_valor_estimado);
                ELSE
                    PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'PERDIDA',
                        'Janela encerrada sem retorno', 'SEED');
                END IF;

            ELSIF o.dt_prevista <= TRUNC(SYSDATE) + 30
                  AND o.fl_grupo_controle = 'N' THEN
                PKG_CLV_MOTOR.TRANSITAR_OBRIGACAO(o.id_obrigacao, 'NOTIFICADA',
                    'Lembrete enviado', 'SEED');
            END IF;

            v_i := v_i + 1;
            IF MOD(v_i, 200) = 0 THEN
                COMMIT;
            END IF;
        END LOOP;

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.DESFECHOS', SQLCODE, SQLERRM);
            RAISE;
    END DESFECHOS;

    PROCEDURE EXECUTAR (
        p_qtd   IN NUMBER DEFAULT 400,
        p_meses IN NUMBER DEFAULT 18
    ) IS
    BEGIN
        LIMPAR;                 DBMS_OUTPUT.PUT_LINE('1/5 limpeza ok');
        BASE;                   DBMS_OUTPUT.PUT_LINE('2/5 clinicas ok');
        HEROIS;                 DBMS_OUTPUT.PUT_LINE('3/5 Thor e Nala ok');
        POPULACAO(p_qtd, p_meses);
                                DBMS_OUTPUT.PUT_LINE('4/5 populacao ok');
        DESFECHOS;              DBMS_OUTPUT.PUT_LINE('5/5 desfechos ok');
    EXCEPTION
        WHEN OTHERS THEN
            PKG_CLV_LOG.REGISTRAR_ERRO('PKG_CLV_SEED.EXECUTAR', SQLCODE,
                            'qtd=' || p_qtd || ' meses=' || p_meses || ' ' || SQLERRM);
            RAISE;
    END EXECUTAR;

END PKG_CLV_SEED;
/


-- ------------------------------------------------------------
-- 4. Conferência final
-- ------------------------------------------------------------
-- O Flyway trata erro de compilação de PL/SQL como warning (ORA-17110): sem
-- esta checagem, um body quebrado passaria e só apareceria no próximo seed.
DECLARE
    v_invalidos VARCHAR2(4000);
BEGIN
    SELECT LISTAGG(object_type || ' ' || object_name, ', ')
             WITHIN GROUP (ORDER BY object_name, object_type)
      INTO v_invalidos
      FROM USER_OBJECTS
     WHERE (object_name LIKE 'PKG\_CLV\_%' ESCAPE '\'
            OR object_name IN ('TR_CLV_AUDIT_OBRIGACAO', 'VW_CLV_OBRIGACAO_DETALHE'))
       AND status <> 'VALID';

    IF v_invalidos IS NOT NULL THEN
        RAISE_APPLICATION_ERROR(-20904,
            'V16 terminou inconsistente. INVALID: ' || v_invalidos
            || '. Ver USER_ERRORS.');
    END IF;
END;
/
