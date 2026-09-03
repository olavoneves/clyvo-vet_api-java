-- ============================================================
-- MIGRATION V8 - Melhorias de banco: gerador de dados de demonstracao
-- ============================================================
-- Ferramental em PL/SQL que popula um banco vazio com clinicas, tutores, pets,
-- consultas e obrigacoes coerentes com o motor da V7. Nada aqui e chamado pela
-- aplicacao: sao procedures de operacao, executadas a mao quando se quer uma
-- base de demonstracao.
--
-- Num banco limpo, depois que a aplicacao subir:
--     BEGIN PR_CLV_SEED_EXECUTAR(p_qtd_pets => 400); END;
--     /
--
-- PR_CLV_SEED_LIMPAR apaga os dados gerados. Nunca rode em base real.
-- ============================================================

CREATE OR REPLACE FUNCTION FN_CLV_SEED_CONSULTA (
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
        PR_CLV_SEED_GERAR(p_clinica, p_pet, v_consulta, p_data);
    END IF;

    RETURN v_consulta;
END FN_CLV_SEED_CONSULTA;
/

CREATE OR REPLACE PROCEDURE PR_CLV_SEED_LIMPAR IS
BEGIN
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
END PR_CLV_SEED_LIMPAR;
/

CREATE OR REPLACE PROCEDURE PR_CLV_SEED_BASE IS
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
            'helena@vidaanimal.com.br', '$2a$10$seedhashplaceholder00000', v_a);

    INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                    ds_email, ds_senha_hash, id_clinica)
    VALUES ('Dr. Rafael Nunes', 'SP-11002', 'Nefrologia',
            'rafael@vidaanimal.com.br', '$2a$10$seedhashplaceholder00000', v_a);

    INSERT INTO TB_CLV_VETERINARIO (nm_veterinario, nr_crmv, ds_especialidade,
                                    ds_email, ds_senha_hash, id_clinica)
    VALUES ('Dra. Marina Costa', 'SP-22001', 'Clinica geral',
            'marina@petcare.com.br', '$2a$10$seedhashplaceholder00000', v_b);

    INSERT INTO TB_CLV_COLABORADOR (nm_colaborador, ds_email, ds_senha_hash,
                                    ds_cargo, id_clinica)
    VALUES ('Patricia Moraes', 'patricia@vidaanimal.com.br',
            '$2a$10$seedhashplaceholder00000', 'ADMINISTRADOR', v_a);

    INSERT INTO TB_CLV_COLABORADOR (nm_colaborador, ds_email, ds_senha_hash,
                                    ds_cargo, id_clinica)
    VALUES ('Diego Tavares', 'diego@petcare.com.br',
            '$2a$10$seedhashplaceholder00000', 'RECEPCAO', v_b);

    COMMIT;
END PR_CLV_SEED_BASE;
/

CREATE OR REPLACE PROCEDURE PR_CLV_SEED_POPULACAO (
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
    DBMS_RANDOM.SEED(20260909);   -- reprodutÃ­vel

    SELECT MIN(id_clinica), MAX(id_clinica)
      INTO v_clinica_a, v_clinica_b FROM TB_CLV_CLINICA;

    FOR i IN 1 .. p_qtd LOOP

        v_clinica := CASE WHEN DBMS_RANDOM.VALUE(0,1) < 0.6
                          THEN v_clinica_a ELSE v_clinica_b END;

        SELECT MIN(id_veterinario) INTO v_vet
          FROM TB_CLV_VETERINARIO WHERE id_clinica = v_clinica;

        -- RaÃ§a aleatÃ³ria; o porte padrÃ£o da raÃ§a vira o porte do pet.
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
                '$2a$10$seedhashplaceholder00000',
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

        -- 2 a 6 consultas na janela histÃ³rica, sempre apÃ³s o nascimento
        v_qtd_c := 2 + ROUND(DBMS_RANDOM.VALUE(0, 4));
        FOR j IN 1 .. v_qtd_c LOOP
            v_data := TRUNC(SYSDATE) - ROUND(DBMS_RANDOM.VALUE(5, p_meses*30));
            IF v_data > v_nasc + 30 THEN
                v_cons := FN_CLV_SEED_CONSULTA(
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
END PR_CLV_SEED_POPULACAO;
/

CREATE OR REPLACE PROCEDURE PR_CLV_SEED_GERAR (
    p_clinica   IN NUMBER,
    p_pet       IN NUMBER,
    p_consulta  IN NUMBER,
    p_data      IN DATE,
    p_horizonte IN NUMBER DEFAULT 550
) IS
    v_qtd NUMBER;
BEGIN
    PR_CLV_GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
        'CONSULTA_REGISTRADA', NULL, p_data, NULL, p_horizonte, v_qtd);

    PR_CLV_GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
        'PESO_ATUALIZADO', NULL, p_data, NULL, p_horizonte, v_qtd);

    IF FN_CLV_FASE_VIDA(p_pet, p_data) = 'SENIOR' THEN
        PR_CLV_GERAR_OBRIGACOES(p_clinica, p_pet, p_consulta,
            'FASE_VIDA_ALTERADA', NULL, p_data, NULL, p_horizonte, v_qtd);
    END IF;
END PR_CLV_SEED_GERAR;
/

CREATE OR REPLACE PROCEDURE PR_CLV_SEED_DESFECHOS (
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
                PR_CLV_TRANSITAR_OBRIGACAO(o.id_obrigacao, 'NOTIFICADA',
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

                PR_CLV_TRANSITAR_OBRIGACAO(o.id_obrigacao, 'AGENDADA',
                    'Tutor agendou', 'SEED', NULL, v_agend);

                INSERT INTO TB_CLV_CONSULTA (dt_consulta, ds_motivo, ds_status,
                                             id_pet, id_veterinario, nr_valor)
                VALUES (o.dt_prevista, SUBSTR(o.nm_etapa,1,500), 'REALIZADA',
                        o.id_pet, v_vet, o.nr_valor_estimado)
                RETURNING id_consulta INTO v_cons;

                UPDATE TB_CLV_AGENDAMENTO SET id_consulta = v_cons
                 WHERE id_agendamento = v_agend;

                PR_CLV_TRANSITAR_OBRIGACAO(o.id_obrigacao, 'CUMPRIDA',
                    'Comparecimento confirmado', 'SEED', NULL, NULL, v_cons,
                    o.nr_valor_estimado);
            ELSE
                PR_CLV_TRANSITAR_OBRIGACAO(o.id_obrigacao, 'PERDIDA',
                    'Janela encerrada sem retorno', 'SEED');
            END IF;

        ELSIF o.dt_prevista <= TRUNC(SYSDATE) + 30
              AND o.fl_grupo_controle = 'N' THEN
            PR_CLV_TRANSITAR_OBRIGACAO(o.id_obrigacao, 'NOTIFICADA',
                'Lembrete enviado', 'SEED');
        END IF;

        v_i := v_i + 1;
        IF MOD(v_i, 200) = 0 THEN
            COMMIT;
        END IF;
    END LOOP;

    COMMIT;
END PR_CLV_SEED_DESFECHOS;
/

CREATE OR REPLACE PROCEDURE PR_CLV_SEED_HEROIS IS
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

    -- ---------- THOR Â· Golden Retriever, ~3 meses ----------
    INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                              ds_canal_preferencial, dt_cadastro, id_clinica)
    VALUES ('Camila Ferreira', 'camila.ferreira@exemplo.com', '11987650001',
            '$2a$10$seedhashplaceholder00000', 'APP', SYSDATE-70, v_clinica)
    RETURNING id_tutor INTO v_tutor;

    SELECT id_raca INTO v_raca FROM TB_CLV_RACA WHERE nm_raca = 'Golden Retriever';

    INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                            ds_pelagem, ds_porte, fl_castrado, ds_status_pet,
                            id_tutor, id_raca, id_clinica)
    VALUES ('Thor', TRUNC(SYSDATE)-95, 'M', 'CHIP900000000001',
            'Dourada', 'GRANDE', 'N', 'ATIVO', v_tutor, v_raca, v_clinica)
    RETURNING id_pet INTO v_pet;

    v_cons := FN_CLV_SEED_CONSULTA(v_clinica, v_pet, v_vet, TRUNC(SYSDATE)-50,
                                   'Primeira consulta de filhote', 6.2, 180);
    v_cons := FN_CLV_SEED_CONSULTA(v_clinica, v_pet, v_vet, TRUNC(SYSDATE)-20,
                                   'Retorno e segunda dose', 9.8, 120);

    -- ---------- NALA Â· Persa, ~8 anos, DRC ----------
    INSERT INTO TB_CLV_TUTOR (nm_tutor, ds_email, nr_telefone, ds_senha_hash,
                              ds_canal_preferencial, dt_cadastro, id_clinica)
    VALUES ('Roberto Almeida', 'roberto.almeida@exemplo.com', '11987650002',
            '$2a$10$seedhashplaceholder00000', 'APP', SYSDATE-700, v_clinica)
    RETURNING id_tutor INTO v_tutor;

    SELECT id_raca INTO v_raca FROM TB_CLV_RACA WHERE nm_raca = 'Persa';

    INSERT INTO TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, nr_microchip,
                            ds_pelagem, ds_porte, fl_castrado, ds_status_pet,
                            id_tutor, id_raca, id_clinica)
    VALUES ('Nala', ADD_MONTHS(TRUNC(SYSDATE), -98), 'F', 'CHIP900000000002',
            'Creme', 'PEQUENO', 'S', 'EM_TRATAMENTO', v_tutor, v_raca, v_clinica)
    RETURNING id_pet INTO v_pet;

    v_cons := FN_CLV_SEED_CONSULTA(v_clinica, v_pet, v_vet,
                                   ADD_MONTHS(TRUNC(SYSDATE),-16),
                                   'Check-up anual', 4.1, 260);
    v_cons := FN_CLV_SEED_CONSULTA(v_clinica, v_pet, v_vet,
                                   ADD_MONTHS(TRUNC(SYSDATE),-10),
                                   'Apatia e perda de peso', 3.6, 320);
    v_cons := FN_CLV_SEED_CONSULTA(v_clinica, v_pet, v_vet_nf,
                                   ADD_MONTHS(TRUNC(SYSDATE),-6),
                                   'Investigacao renal', 3.4, 420,
                                   'Doenca renal cronica estagio 2');

    -- DiagnÃ³stico crÃ´nico ativa o protocolo de monitoramento renal.
    SELECT id_tipo_condicao INTO v_cond
      FROM TB_CLV_TIPO_CONDICAO WHERE nm_condicao = 'Doenca renal cronica';

    INSERT INTO TB_CLV_CONDICAO_PET (id_pet, id_tipo_condicao, dt_diagnostico,
                                     ds_observacao, fl_ativo, id_consulta_origem)
    VALUES (v_pet, v_cond, ADD_MONTHS(TRUNC(SYSDATE),-6),
            'Estagio 2 IRIS. Dieta renal iniciada.', 'S', v_cons);

    PR_CLV_GERAR_OBRIGACOES(v_clinica, v_pet, v_cons,
        'CONDICAO_DIAGNOSTICADA', NULL,
        ADD_MONTHS(TRUNC(SYSDATE),-6), NULL, 400, v_qtd);

    COMMIT;
END PR_CLV_SEED_HEROIS;
/

CREATE OR REPLACE PROCEDURE PR_CLV_SEED_EXECUTAR (
    p_qtd   IN NUMBER DEFAULT 400,
    p_meses IN NUMBER DEFAULT 18
) IS
BEGIN
    PR_CLV_SEED_LIMPAR;     DBMS_OUTPUT.PUT_LINE('1/5 limpeza ok');
    PR_CLV_SEED_BASE;       DBMS_OUTPUT.PUT_LINE('2/5 clinicas ok');
    PR_CLV_SEED_HEROIS;     DBMS_OUTPUT.PUT_LINE('3/5 Thor e Nala ok');
    PR_CLV_SEED_POPULACAO(p_qtd, p_meses);
                            DBMS_OUTPUT.PUT_LINE('4/5 populacao ok');
    PR_CLV_SEED_DESFECHOS;  DBMS_OUTPUT.PUT_LINE('5/5 desfechos ok');
END PR_CLV_SEED_EXECUTAR;
/

