#!/bin/bash
# ============================================================
#  CENA 2 — Conectar na VM, clonar repo e subir containers
#  LOCAL: Git Bash no Windows → SSH na VM
# ============================================================
#
#  ONDE: Git Bash local (Windows)
#
#  PASSO 1 — Conectar via SSH (substitua IP_PUBLICO):
#  ssh azureuser@IP_PUBLICO
#  Senha: PetFlow@2026!
#
#  bash /c/Users/StartSe/devops/scenes/c2.sh
# ------------------------------------------------------------
#  >>> A PARTIR DAQUI, VOCE ESTA DENTRO DA VM <<<
# ------------------------------------------------------------

# PASSO 2 — Clonar o repositorio
cd ~
git clone https://github.com/olavoneves/clyvo-vet_api-java.git
cd clyvo-vet_api-java

# PASSO 3 — Subir containers em background
docker compose up -d --build

# PASSO 4 — Aguardar Oracle ficar saudavel (~2-3 min)
echo ""
echo ">>> Aguardando Oracle inicializar..."
echo ""
until docker inspect --format='{{.State.Health.Status}}' oracle-clyvovet 2>/dev/null | grep -q "healthy"; do
  echo "    Oracle ainda iniciando..."
  sleep 15
done
echo ">>> Oracle pronto!"

# PASSO 5 — Verificar containers rodando
docker compose ps

# ------------------------------------------------------------
#  >>> CONTINUA DENTRO DA VM PARA A CENA 3 <<<
# ------------------------------------------------------------
