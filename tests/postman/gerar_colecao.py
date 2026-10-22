#!/usr/bin/env python3
"""
Gera a colecao Postman da Parte B (automacao de testes) de Compliance.

  python tests/postman/gerar_colecao.py

Escreve petflow-testes-automatizados.postman_collection.json ao lado deste
arquivo. A colecao e gerada, e nao editada a mao, porque os scripts de teste
do Postman ficam dentro do JSON como listas de linhas - ilegiveis para revisar
e faceis de corromper. Importe o arquivo gerado no Postman ou rode com Newman:

  npx newman run tests/postman/petflow-testes-automatizados.postman_collection.json \
      -e tests/postman/petflow-azure.postman_environment.json

Cinco casos automatizados, um por pasta, executados em sequencia. Cada
execucao cria um tutor com e-mail unico e apaga tudo o que criou no final:
pode ser repetida sem preparar dados. Sao 2 logins por execucao - abaixo do
limite de 5 por minuto da aplicacao.

Projeto Clyvo Vet - Compliance - RM561940
"""
import json
from pathlib import Path

SAIDA = Path(__file__).with_name("petflow-testes-automatizados.postman_collection.json")


def requisicao(nome, metodo, rota, testes, corpo=None, token="token", pre=None):
    cabecalhos = [{"key": "Content-Type", "value": "application/json"}]
    if token:
        cabecalhos.append({"key": "Authorization", "value": f"Bearer {{{{{token}}}}}"})
    item = {
        "name": nome,
        "request": {
            "method": metodo,
            "header": cabecalhos,
            "url": {"raw": "{{baseUrl}}" + rota, "host": ["{{baseUrl}}"],
                    "path": [p for p in rota.strip("/").split("/")]},
        },
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": testes}}],
    }
    if corpo is not None:
        item["request"]["body"] = {"mode": "raw", "raw": json.dumps(corpo, ensure_ascii=False, indent=2),
                                   "options": {"raw": {"language": "json"}}}
    if pre:
        item["event"].insert(0, {"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre}})
    return item


TUTOR = {"nome": "Tutor Automacao QA", "email": "{{emailTutor}}", "telefone": "11944443333",
         "senha": "{{senhaTutor}}", "canalPreferencial": "APP", "clinicaId": 1}
PET = {"nome": "Bolt", "dtNascimento": "2022-01-20", "sexo": "M", "porte": "MEDIO", "castrado": False,
       "status": "ATIVO", "tutorId": "{{tutorId}}", "racaId": 1}


colecao = {
    "info": {
        "name": "PetFlow - Testes automatizados (Compliance Sprint 4)",
        "description": "Parte B do plano de testes: 5 casos automatizados contra o Web App publicado. "
                       "Gerada por tests/postman/gerar_colecao.py.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [{
        "listen": "prerequest",
        "script": {"type": "text/javascript", "exec": [
            "// E-mail e senha unicos por execucao: o e-mail do tutor e UNIQUE no banco,",
            "// e o tutor e criado e apagado pela propria colecao - nenhuma senha fixa.",
            "if (!pm.collectionVariables.get('emailTutor')) {",
            "    pm.collectionVariables.set('emailTutor', `qa.auto.${Date.now()}@exemplo.com`);",
            "    pm.collectionVariables.set('senhaTutor', `qa-${Math.random().toString(36).slice(2, 12)}`);",
            "}",
        ]},
    }],
    "variable": [
        {"key": "senhaTutor", "value": ""},
        {"key": "emailTutor", "value": ""},
        {"key": "token", "value": ""},
        {"key": "tokenTutor", "value": ""},
        {"key": "tutorId", "value": ""},
        {"key": "petId", "value": ""},
    ],
    "item": [
        {
            "name": "CA01 - Saude e autenticacao",
            "item": [
                requisicao("Aplicacao no ar e conectada ao banco", "GET", "/actuator/health", token=None, testes=[
                    "pm.test('HTTP 200', () => pm.response.to.have.status(200));",
                    "pm.test('status UP (inclui o datasource Oracle)', () => pm.expect(pm.response.json().status).to.eql('UP'));",
                ]),
                requisicao("Login do colaborador", "POST", "/api/auth/login", token=None,
                           corpo={"email": "{{emailColaborador}}", "senha": "{{senhaColaborador}}", "tipo": "COLABORADOR"},
                           testes=[
                    "pm.test('HTTP 200', () => pm.response.to.have.status(200));",
                    "const corpo = pm.response.json();",
                    "pm.test('emite accessToken', () => pm.expect(corpo.accessToken).to.be.a('string').and.not.empty);",
                    "pm.test('colaborador nao recebe refreshToken', () => pm.expect(corpo.refreshToken).to.be.null);",
                    "pm.test('perfil COLABORADOR', () => pm.expect(corpo.tipo).to.eql('COLABORADOR'));",
                    "pm.collectionVariables.set('token', corpo.accessToken);",
                ]),
            ],
        },
        {
            "name": "CA02 - Cadastro e alteracao de tutor e pet",
            "item": [
                requisicao("Cadastrar tutor (rota publica)", "POST", "/api/tutores", token=None, corpo=TUTOR, testes=[
                    "pm.test('HTTP 201', () => pm.response.to.have.status(201));",
                    "const corpo = pm.response.json();",
                    "pm.test('devolve o id gerado', () => pm.expect(corpo.id).to.be.a('number'));",
                    "pm.test('grava nome e canal', () => {",
                    "    pm.expect(corpo.nome).to.eql('Tutor Automacao QA');",
                    "    pm.expect(corpo.canalPreferencial).to.eql('APP');",
                    "});",
                    "pm.collectionVariables.set('tutorId', corpo.id);",
                ]),
                requisicao("Cadastrar pet do tutor", "POST", "/api/pets", corpo=PET, testes=[
                    "pm.test('HTTP 201', () => pm.response.to.have.status(201));",
                    "const corpo = pm.response.json();",
                    "pm.test('pet vinculado ao tutor', () => pm.expect(corpo.tutorId).to.eql(Number(pm.collectionVariables.get('tutorId'))));",
                    "pm.test('status ATIVO', () => pm.expect(corpo.status).to.eql('ATIVO'));",
                    "pm.collectionVariables.set('petId', corpo.id);",
                ]),
                requisicao("Alterar o nome do pet", "PUT", "/api/pets/{{petId}}", corpo={**PET, "nome": "Bolt Veloz"}, testes=[
                    "pm.test('HTTP 200', () => pm.response.to.have.status(200));",
                    "pm.test('nome alterado', () => pm.expect(pm.response.json().nome).to.eql('Bolt Veloz'));",
                ]),
            ],
        },
        {
            "name": "CA03 - Validacao e integridade",
            "item": [
                requisicao("Recusar pet sem campos obrigatorios", "POST", "/api/pets",
                           corpo={k: v for k, v in {**PET, "nome": ""}.items() if k != "tutorId"}, testes=[
                    "pm.test('HTTP 422', () => pm.response.to.have.status(422));",
                    "const erros = pm.response.json().errors;",
                    "pm.test('aponta o nome em branco', () => pm.expect(erros.nome).to.eql('must not be blank'));",
                    "pm.test('aponta o tutor ausente', () => pm.expect(erros.tutorId).to.eql('must not be null'));",
                ]),
                requisicao("Recusar exclusao de tutor com pet vinculado", "DELETE", "/api/tutores/{{tutorId}}", testes=[
                    "pm.test('HTTP 409, nunca 500', () => pm.response.to.have.status(409));",
                    "pm.test('conflito de integridade', () => pm.expect(pm.response.json().title).to.eql('Data Conflict'));",
                ]),
            ],
        },
        {
            "name": "CA04 - Controle de acesso por perfil",
            "item": [
                requisicao("Login do tutor", "POST", "/api/auth/login", token=None,
                           corpo={"email": "{{emailTutor}}", "senha": "{{senhaTutor}}", "tipo": "TUTOR"}, testes=[
                    "pm.test('HTTP 200', () => pm.response.to.have.status(200));",
                    "const corpo = pm.response.json();",
                    "pm.test('perfil TUTOR', () => pm.expect(corpo.tipo).to.eql('TUTOR'));",
                    "pm.test('tutor recebe refreshToken', () => pm.expect(corpo.refreshToken).to.be.a('string').and.not.empty);",
                    "pm.collectionVariables.set('tokenTutor', corpo.accessToken);",
                ]),
                requisicao("Tutor barrado na API de gestao", "GET", "/api/tutores", token="tokenTutor", testes=[
                    "pm.test('HTTP 403', () => pm.response.to.have.status(403));",
                ]),
                requisicao("Tutor ve apenas os proprios pets", "GET", "/api/tutor/pets", token="tokenTutor", testes=[
                    "pm.test('HTTP 200', () => pm.response.to.have.status(200));",
                    "pm.test('somente o pet Bolt Veloz', () => {",
                    "    const nomes = pm.response.json().content.map(p => p.nome);",
                    "    pm.expect(nomes).to.eql(['Bolt Veloz']);",
                    "});",
                ]),
            ],
        },
        {
            "name": "CA05 - Exclusao na ordem correta",
            "item": [
                requisicao("Excluir o pet", "DELETE", "/api/pets/{{petId}}", testes=[
                    "pm.test('HTTP 204', () => pm.response.to.have.status(204));",
                ]),
                requisicao("Excluir o tutor", "DELETE", "/api/tutores/{{tutorId}}", testes=[
                    "pm.test('HTTP 204', () => pm.response.to.have.status(204));",
                ]),
                requisicao("Tutor excluido nao e mais encontrado", "GET", "/api/tutores/{{tutorId}}", testes=[
                    "pm.test('HTTP 404', () => pm.response.to.have.status(404));",
                    "// limpa e-mail e senha para a proxima execucao gerar outros",
                    "pm.collectionVariables.set('emailTutor', '');",
                    "pm.collectionVariables.set('senhaTutor', '');",
                ]),
            ],
        },
    ],
}

texto = json.dumps(colecao, ensure_ascii=False, indent=2)
# Ids numericos vao sem aspas no corpo, para a API receber Long e nao String.
texto = texto.replace('\\"{{tutorId}}\\"', "{{tutorId}}")
SAIDA.write_text(texto + "\n", encoding="utf-8")
print(f"Gerado {SAIDA.name}")
