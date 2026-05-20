#!/bin/bash
# ============================================================
#  CENA 3 — Validar requisitos do entregavel
#  LOCAL: Dentro da VM (continuacao da Cena 2)
# ============================================================
#
#  >>> VOCE AINDA ESTA DENTRO DA VM <<<
#
#  bash /c/Users/StartSe/devops/scenes/c3.sh

echo ""
echo "============================================="
echo "  VALIDACAO DOS REQUISITOS"
echo "============================================="

# 2.1 — Projeto executando em background
echo ""
echo ">>> Containers rodando em background:"
docker compose ps

# 2.2 — Usuario nao-root
echo ""
echo ">>> Usuario da aplicacao (deve ser appuser, nao root):"
docker exec api-clyvovet whoami

# 2.3 — Volume nomeado
echo ""
echo ">>> Volumes Docker (deve aparecer oracle-data):"
docker volume ls

echo ""
echo "============================================="
echo "  VALIDACAO CONCLUIDA"
echo "============================================="

# ------------------------------------------------------------
#  >>> CONTINUA DENTRO DA VM PARA A CENA 4 <<<
# ------------------------------------------------------------
