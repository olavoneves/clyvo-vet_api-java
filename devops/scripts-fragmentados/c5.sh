#!/bin/bash
# ============================================================
#  CENA 5 — Persistencia no Oracle + Encerramento
#  LOCAL: Dentro da VM → depois volta pro Git Bash local
# ============================================================
#
#  >>> VOCE AINDA ESTA DENTRO DA VM <<<
#
#  bash /c/Users/StartSe/devops/scenes/c5.sh

echo ""
echo "============================================="
echo "  PERSISTENCIA — Consulta direta no Oracle"
echo "============================================="
echo ""

# ----------------------------------------------------------
#  PASSO 1 — Consultar dados no Oracle
# ----------------------------------------------------------

echo ">>> Conectando no Oracle..."
echo ""

docker exec -it oracle-clyvovet sqlplus petflow/PetFlow2026@//localhost:1521/PETFLOWDB <<EOF
SET LINESIZE 200;
SET PAGESIZE 50;
SELECT * FROM TB_CLV_ESPECIE;
SELECT * FROM TB_CLV_CLINICA;
SELECT * FROM TB_CLV_VETERINARIO;
EXIT;
EOF

echo ""
echo "============================================="
echo "  PERSISTENCIA CONFIRMADA!"
echo "============================================="
echo ""

# ----------------------------------------------------------
#  PASSO 2 — Sair da VM
# ----------------------------------------------------------
#
#  Digitar: exit
#
# ------------------------------------------------------------
#  >>> AGORA VOCE ESTA DE VOLTA NO GIT BASH LOCAL <<<
# ------------------------------------------------------------
#
#  PASSO 3 — Deletar recursos (OBRIGATORIO)
#
#  bash /c/Users/StartSe/devops/scripts/cleanup-clyvovet.sh
#
#  PASSO 4 — Tirar print da evidencia de remocao
#
#  Verificar com:
#  az group show --name rg-clyvovet-devops --query properties.provisioningState --output tsv
#
#  Quando retornar "not found" = tudo removido. Tirar print!
#
# ============================================================
