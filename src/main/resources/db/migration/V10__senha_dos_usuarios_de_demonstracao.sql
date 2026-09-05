-- ============================================================
-- CLYVO VET — V10 — Senha dos usuários de demonstração
--
-- Nenhum usuário nascido do seed conseguia entrar. As procedures da V8
-- gravavam '$2a$10$seedhashplaceholder00000' em ds_senha_hash: tem a forma
-- de um BCrypt, mas não é um. Depois do prefixo de custo o algoritmo exige
-- 53 caracteres (22 de salt + 31 de hash) e ali só há 24, então o
-- BCryptPasswordEncoder do AppConfig descarta o valor pelo formato, antes
-- mesmo de comparar a senha. Toda tentativa de login virava credencial
-- inválida — veterinário, colaborador e tutor, nas duas clínicas.
--
-- A V8 já foi corrigida na origem, e num banco vazio isso basta. Aqui não:
-- o baseline do Flyway está em 8, a V8 nunca torna a executar, e o
-- placeholder continuaria gravado no que já foi semeado. Daí esta migration
-- — mesma divisão de trabalho que a V9 documenta.
--
-- A senha passa a ser 'Clyvo@2026' para todos eles. É base de demonstração,
-- não de produção: o hash é público de propósito. O master@clyvovet.com, que
-- nasce do DataInitializer e não do seed, não é tocado aqui.
--
-- Idempotente: o WHERE só alcança quem ainda tem o placeholder. Rodar duas
-- vezes, ou rodar num banco já corrigido à mão, não muda nada — e senha que
-- um usuário de verdade tenha trocado fica intacta.
-- ============================================================

UPDATE TB_CLV_VETERINARIO
   SET ds_senha_hash = '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW'
 WHERE ds_senha_hash LIKE '$2a$10$seedhash%';

UPDATE TB_CLV_COLABORADOR
   SET ds_senha_hash = '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW'
 WHERE ds_senha_hash LIKE '$2a$10$seedhash%';

UPDATE TB_CLV_TUTOR
   SET ds_senha_hash = '$2b$10$37zKe76f547bIVAQx0IcceqDze29cYAr.vhPhPXOVXRfEUY/3ZJwW'
 WHERE ds_senha_hash LIKE '$2a$10$seedhash%';

COMMIT;
