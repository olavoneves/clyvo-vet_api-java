#!/bin/bash
# ============================================================
#  CENA 4 — Testar CRUD completo (IP publico)
#  LOCAL: Dentro da VM (continuacao da Cena 3)
# ============================================================
#
#  >>> VOCE AINDA ESTA DENTRO DA VM <<<
#
#  IMPORTANTE: Substitua IP_PUBLICO pelo IP real da VM
#  O token JWT expira em 15 minutos. Se expirar, faca login novamente.
#
#  bash /c/Users/StartSe/devops/scenes/c4.sh

IP_PUBLICO=$(curl -s ifconfig.me)

echo ""
echo "============================================="
echo "  CRUD — http://$IP_PUBLICO:8080"
echo "============================================="


# ----------------------------------------------------------
#  PASSO 1 — Criar clinica (rota publica, sem token)
# ----------------------------------------------------------

echo ""
echo ">>> POST — Criar clinica:"
curl -s -X POST http://$IP_PUBLICO:8080/clinicas \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "CLYVO Vet SP",
    "cnpj": "12345678000199",
    "logradouro": "Av. Paulista 1000",
    "cidade": "Sao Paulo",
    "estado": "SP"
  }' | python3 -m json.tool
echo ""


# ----------------------------------------------------------
#  PASSO 2 — Criar veterinario (rota publica, sem token)
# ----------------------------------------------------------

echo ""
echo ">>> POST — Criar veterinario:"
curl -s -X POST http://$IP_PUBLICO:8080/veterinarios \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Dr. Paulo Costa",
    "crmv": "SP-67890",
    "email": "paulo@clyvovet.com",
    "senha": "PetFlow2026",
    "clinicaId": 1
  }' | python3 -m json.tool
echo ""


# ----------------------------------------------------------
#  PASSO 3 — Login (obter token JWT)
# ----------------------------------------------------------

echo ""
echo ">>> POST — Login:"
LOGIN_RESPONSE=$(curl -s -X POST http://$IP_PUBLICO:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "paulo@clyvovet.com",
    "senha": "PetFlow2026",
    "tipo": "VETERINARIO"
  }')

echo $LOGIN_RESPONSE | python3 -m json.tool

# Extrair token
TOKEN=$(echo $LOGIN_RESPONSE | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo ""
echo ">>> Token obtido com sucesso!"
echo ""


# ----------------------------------------------------------
#  PASSO 4 — GET (listar especies - vazio)
# ----------------------------------------------------------

echo ""
echo ">>> GET — Listar especies (vazio):"
curl -s http://$IP_PUBLICO:8080/especies \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
echo ""


# ----------------------------------------------------------
#  PASSO 5 — POST (inserir 2 especies)
# ----------------------------------------------------------

echo ""
echo ">>> POST — Inserir Canino:"
curl -s -X POST http://$IP_PUBLICO:8080/especies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome": "Canino", "descricao": "Caes domesticos"}' | python3 -m json.tool
echo ""

echo ">>> POST — Inserir Felino:"
curl -s -X POST http://$IP_PUBLICO:8080/especies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome": "Felino", "descricao": "Gatos domesticos"}' | python3 -m json.tool
echo ""


# ----------------------------------------------------------
#  PASSO 6 — GET (listar apos inserts)
# ----------------------------------------------------------

echo ""
echo ">>> GET — Listar apos inserts:"
curl -s http://$IP_PUBLICO:8080/especies \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
echo ""


# ----------------------------------------------------------
#  PASSO 7 — PUT (alterar Canino)
# ----------------------------------------------------------

echo ""
echo ">>> PUT — Alterar Canino:"
curl -s -X PUT http://$IP_PUBLICO:8080/especies/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nome": "Canino", "descricao": "Caes domesticos e selvagens"}' | python3 -m json.tool
echo ""


# ----------------------------------------------------------
#  PASSO 8 — DELETE (remover Felino)
# ----------------------------------------------------------

echo ""
echo ">>> DELETE — Remover Felino (id=2):"
curl -s -X DELETE http://$IP_PUBLICO:8080/especies/2 \
  -H "Authorization: Bearer $TOKEN" -w "\nHTTP Status: %{http_code}\n"
echo ""


# ----------------------------------------------------------
#  PASSO 9 — GET (resultado final)
# ----------------------------------------------------------

echo ""
echo ">>> GET — Resultado final:"
curl -s http://$IP_PUBLICO:8080/especies \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
echo ""


echo ""
echo "============================================="
echo "  CRUD CONCLUIDO COM SUCESSO"
echo "============================================="
echo ""

# ------------------------------------------------------------
#  >>> CONTINUA DENTRO DA VM PARA A CENA 5 <<<
# ------------------------------------------------------------  
