-- ============================================================
-- CLYVO VET — V14 — INATIVO em ds_status_pet
--
-- O aplicativo do tutor tem um botão de remover pet. Até aqui ele fazia
-- remoção física, e isso estava errado por dois motivos que só aparecem
-- juntos.
--
-- O primeiro é o que NÃO acontecia: nenhuma das sete tabelas que apontam para
-- TB_CLV_PET tem ON DELETE CASCADE, e a entidade Pet não tem coleção mapeada,
-- então o histórico nunca correu risco — o Oracle recusava com ORA-02292. O
-- segundo é o que acontecia: por causa disso, o botão só funcionava em pet sem
-- consulta, sem vacina e sem obrigação. Em qualquer pet real ele devolvia 409.
--
-- A resposta é inativar, e inativar precisa de um estado que a tabela aceite.
-- Os quatro valores existentes são afirmações clínicas sobre o animal — ÓBITO e
-- PERDIDO principalmente — e gravar um deles porque alguém tocou em "remover"
-- corromperia o prontuário e, com ele, a coorte do painel. Daí o quinto valor.
--
-- O QUE INATIVO SIGNIFICA: o tutor não quer mais ver este pet no aplicativo.
-- Não é um fato clínico e não muda nada para a clínica — a equipe continua
-- enxergando o pet em /api/pets e nas telas de gestão, e as obrigações dele
-- continuam contando no painel. É por isso que o motor de protocolo não olha
-- para este valor: ele não é um estado do animal, é uma preferência de
-- visualização do dono.
--
-- POR QUE O BLOCO PL/SQL EM VEZ DE UM DROP DIRETO
-- O schema da FIAP não nasceu destas migrations (V0–V8 foram aplicadas à mão e
-- o Flyway está com baseline=8), então não há garantia de que CHK_PET_STATUS
-- exista lá com este nome. Um DROP CONSTRAINT direto falharia com ORA-02443 e
-- deixaria a migration pela metade num banco compartilhado. Aqui ele só cai se
-- estiver de pé, e a constraint nova é criada nos dois casos.
-- ============================================================

DECLARE
    l_existe NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO l_existe
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'TB_CLV_PET'
       AND CONSTRAINT_NAME = 'CHK_PET_STATUS';

    IF l_existe > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE TB_CLV_PET DROP CONSTRAINT CHK_PET_STATUS';
    END IF;

    EXECUTE IMMEDIATE q'[
        ALTER TABLE TB_CLV_PET ADD CONSTRAINT CHK_PET_STATUS
            CHECK (ds_status_pet IN ('ATIVO','EM_TRATAMENTO','OBITO','PERDIDO','INATIVO'))
    ]';
END;
/

COMMENT ON COLUMN TB_CLV_PET.ds_status_pet IS
    'ATIVO, EM_TRATAMENTO, OBITO e PERDIDO sao estado clinico do animal. INATIVO nao e: significa que o tutor removeu o pet do aplicativo, e so esconde o pet das listagens dele - a clinica continua enxergando tudo.';
