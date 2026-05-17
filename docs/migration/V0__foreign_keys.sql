-- ============================================================
-- MIGRATION V0 - Foreign Keys de todas as tabelas
-- ============================================================
-- Responsável execução: Analista / DBA
-- Executar ANTES da aplicação subir pela primeira vez.
-- Todas as tabelas referenciadas devem existir antes deste script.
-- Ordem de execução respeita dependências entre tabelas.
-- ============================================================
-- NOTA: TB_CLV_REFRESH_TOKEN.nr_id_usuario NÃO recebe FK pois é
-- polimórfico (pode referenciar TUTOR ou VETERINARIO).
-- ============================================================


-- ============================================================
-- TB_CLV_RACA
-- Depende: TB_CLV_ESPECIE
-- ============================================================
ALTER TABLE TB_CLV_RACA
    ADD CONSTRAINT fk_raca_especie
    FOREIGN KEY (id_especie)
    REFERENCES TB_CLV_ESPECIE (id_especie);


-- ============================================================
-- TB_CLV_VETERINARIO
-- Depende: TB_CLV_CLINICA
-- ============================================================
ALTER TABLE TB_CLV_VETERINARIO
    ADD CONSTRAINT fk_vet_clinica
    FOREIGN KEY (id_clinica)
    REFERENCES TB_CLV_CLINICA (id_clinica);


-- ============================================================
-- TB_CLV_PET
-- Depende: TB_CLV_TUTOR, TB_CLV_RACA
-- ============================================================
ALTER TABLE TB_CLV_PET
    ADD CONSTRAINT fk_pet_tutor
    FOREIGN KEY (id_tutor)
    REFERENCES TB_CLV_TUTOR (id_tutor);

ALTER TABLE TB_CLV_PET
    ADD CONSTRAINT fk_pet_raca
    FOREIGN KEY (id_raca)
    REFERENCES TB_CLV_RACA (id_raca);


-- ============================================================
-- TB_CLV_CONSULTA
-- Depende: TB_CLV_PET, TB_CLV_VETERINARIO
-- ============================================================
ALTER TABLE TB_CLV_CONSULTA
    ADD CONSTRAINT fk_consulta_pet
    FOREIGN KEY (id_pet)
    REFERENCES TB_CLV_PET (id_pet);

ALTER TABLE TB_CLV_CONSULTA
    ADD CONSTRAINT fk_consulta_vet
    FOREIGN KEY (id_veterinario)
    REFERENCES TB_CLV_VETERINARIO (id_veterinario);


-- ============================================================
-- TB_CLV_AGENDAMENTO
-- Depende: TB_CLV_PET, TB_CLV_VETERINARIO, TB_CLV_CONSULTA (nullable)
-- ============================================================
ALTER TABLE TB_CLV_AGENDAMENTO
    ADD CONSTRAINT fk_agend_pet
    FOREIGN KEY (id_pet)
    REFERENCES TB_CLV_PET (id_pet);

ALTER TABLE TB_CLV_AGENDAMENTO
    ADD CONSTRAINT fk_agend_vet
    FOREIGN KEY (id_veterinario)
    REFERENCES TB_CLV_VETERINARIO (id_veterinario);

-- id_consulta é nullable (agendamento ainda não virou consulta)
ALTER TABLE TB_CLV_AGENDAMENTO
    ADD CONSTRAINT fk_agend_consulta
    FOREIGN KEY (id_consulta)
    REFERENCES TB_CLV_CONSULTA (id_consulta);


-- ============================================================
-- TB_CLV_ANAMNESE
-- Depende: TB_CLV_CONSULTA (1:1 — unique já na coluna)
-- ============================================================
ALTER TABLE TB_CLV_ANAMNESE
    ADD CONSTRAINT fk_anamnese_consulta
    FOREIGN KEY (id_consulta)
    REFERENCES TB_CLV_CONSULTA (id_consulta);


-- ============================================================
-- TB_CLV_PRESCRICAO
-- Depende: TB_CLV_CONSULTA, TB_CLV_MEDICAMENTO
-- ============================================================
ALTER TABLE TB_CLV_PRESCRICAO
    ADD CONSTRAINT fk_prescricao_consulta
    FOREIGN KEY (id_consulta)
    REFERENCES TB_CLV_CONSULTA (id_consulta);

ALTER TABLE TB_CLV_PRESCRICAO
    ADD CONSTRAINT fk_prescricao_medicamento
    FOREIGN KEY (id_medicamento)
    REFERENCES TB_CLV_MEDICAMENTO (id_medicamento);


-- ============================================================
-- TB_CLV_EXAME
-- Depende: TB_CLV_CONSULTA, TB_CLV_VETERINARIO (nullable)
-- ============================================================
ALTER TABLE TB_CLV_EXAME
    ADD CONSTRAINT fk_exame_consulta
    FOREIGN KEY (id_consulta)
    REFERENCES TB_CLV_CONSULTA (id_consulta);

-- id_vet_solicitante é nullable
ALTER TABLE TB_CLV_EXAME
    ADD CONSTRAINT fk_exame_vet_solicitante
    FOREIGN KEY (id_vet_solicitante)
    REFERENCES TB_CLV_VETERINARIO (id_veterinario);


-- ============================================================
-- TB_CLV_APLICACAO_VACINA
-- Depende: TB_CLV_PET, TB_CLV_TIPO_VACINA, TB_CLV_VETERINARIO,
--          TB_CLV_CONSULTA (nullable)
-- ============================================================
ALTER TABLE TB_CLV_APLICACAO_VACINA
    ADD CONSTRAINT fk_vacina_pet
    FOREIGN KEY (id_pet)
    REFERENCES TB_CLV_PET (id_pet);

ALTER TABLE TB_CLV_APLICACAO_VACINA
    ADD CONSTRAINT fk_vacina_tipo
    FOREIGN KEY (id_tipo_vacina)
    REFERENCES TB_CLV_TIPO_VACINA (id_tipo_vacina);

ALTER TABLE TB_CLV_APLICACAO_VACINA
    ADD CONSTRAINT fk_vacina_vet
    FOREIGN KEY (id_veterinario)
    REFERENCES TB_CLV_VETERINARIO (id_veterinario);

-- id_consulta é nullable (vacina pode ser aplicada fora de consulta)
ALTER TABLE TB_CLV_APLICACAO_VACINA
    ADD CONSTRAINT fk_vacina_consulta
    FOREIGN KEY (id_consulta)
    REFERENCES TB_CLV_CONSULTA (id_consulta);


-- ============================================================
-- TB_CLV_ALERGIA_PET
-- Depende: TB_CLV_PET, TB_CLV_TIPO_ALERGIA, TB_CLV_CONSULTA (nullable)
-- ============================================================
ALTER TABLE TB_CLV_ALERGIA_PET
    ADD CONSTRAINT fk_alergia_pet
    FOREIGN KEY (id_pet)
    REFERENCES TB_CLV_PET (id_pet);

ALTER TABLE TB_CLV_ALERGIA_PET
    ADD CONSTRAINT fk_alergia_tipo
    FOREIGN KEY (id_tipo_alergia)
    REFERENCES TB_CLV_TIPO_ALERGIA (id_tipo_alergia);

-- id_consulta_origem é nullable
ALTER TABLE TB_CLV_ALERGIA_PET
    ADD CONSTRAINT fk_alergia_consulta_origem
    FOREIGN KEY (id_consulta_origem)
    REFERENCES TB_CLV_CONSULTA (id_consulta);


-- ============================================================
-- TB_CLV_CONDICAO_PET
-- Depende: TB_CLV_PET, TB_CLV_TIPO_CONDICAO, TB_CLV_CONSULTA (nullable)
-- ============================================================
ALTER TABLE TB_CLV_CONDICAO_PET
    ADD CONSTRAINT fk_condicao_pet
    FOREIGN KEY (id_pet)
    REFERENCES TB_CLV_PET (id_pet);

ALTER TABLE TB_CLV_CONDICAO_PET
    ADD CONSTRAINT fk_condicao_tipo
    FOREIGN KEY (id_tipo_condicao)
    REFERENCES TB_CLV_TIPO_CONDICAO (id_tipo_condicao);

-- id_consulta_origem é nullable
ALTER TABLE TB_CLV_CONDICAO_PET
    ADD CONSTRAINT fk_condicao_consulta_origem
    FOREIGN KEY (id_consulta_origem)
    REFERENCES TB_CLV_CONSULTA (id_consulta);


-- ============================================================
-- TB_CLV_SENSOR_IOT
-- Depende: TB_CLV_TIPO_SENSOR, TB_CLV_PET
-- ============================================================
ALTER TABLE TB_CLV_SENSOR_IOT
    ADD CONSTRAINT fk_sensor_tipo
    FOREIGN KEY (id_tipo_sensor)
    REFERENCES TB_CLV_TIPO_SENSOR (id_tipo_sensor);

ALTER TABLE TB_CLV_SENSOR_IOT
    ADD CONSTRAINT fk_sensor_pet
    FOREIGN KEY (id_pet)
    REFERENCES TB_CLV_PET (id_pet);


-- ============================================================
-- TB_CLV_LEITURA_IOT
-- Depende: TB_CLV_SENSOR_IOT
-- ============================================================
ALTER TABLE TB_CLV_LEITURA_IOT
    ADD CONSTRAINT fk_leitura_sensor
    FOREIGN KEY (id_sensor)
    REFERENCES TB_CLV_SENSOR_IOT (id_sensor);


-- ============================================================
-- TB_CLV_ALERTA_IOT
-- Depende: TB_CLV_LEITURA_IOT, TB_CLV_CONSULTA (nullable)
-- ============================================================
ALTER TABLE TB_CLV_ALERTA_IOT
    ADD CONSTRAINT fk_alerta_leitura
    FOREIGN KEY (id_leitura)
    REFERENCES TB_CLV_LEITURA_IOT (id_leitura);

-- id_consulta é nullable
ALTER TABLE TB_CLV_ALERTA_IOT
    ADD CONSTRAINT fk_alerta_consulta
    FOREIGN KEY (id_consulta)
    REFERENCES TB_CLV_CONSULTA (id_consulta);


-- ============================================================
-- FIM DO SCRIPT
-- ============================================================
-- Tabelas SEM FK (são raiz / lookup):
--   TB_CLV_ESPECIE, TB_CLV_CLINICA, TB_CLV_TUTOR,
--   TB_CLV_MEDICAMENTO, TB_CLV_TIPO_VACINA, TB_CLV_TIPO_SENSOR,
--   TB_CLV_TIPO_CONDICAO, TB_CLV_TIPO_ALERGIA, TB_CLV_LOG_ERRO,
--   TB_CLV_REFRESH_TOKEN (nr_id_usuario polimórfico — sem FK)
-- ============================================================
