# Compliance, Quality Assurance & Tests — Entrega da Sprint 4

**Projeto:** PetFlow — Challenge Clyvo Vet · **Turma:** 2TDSR
**Professor:** Felipe Braz Farias Santos

| RM | Integrante |
|---|---|
| RM561940 | Pedro Henrique Dias Franca |
| RM562248 | Felipe Conte |
| RM562906 | Altamir Lima |
| RM563558 | Olavo Porto Neves |
| RM564495 | Luiz Gustavo Goncalves |

## Links da entrega

| Item | Link |
|---|---|
| Azure Boards — backlog, release plan e plano de testes manuais | https://dev.azure.com/RM561940/PetFlow%20-%20Challenge |
| Vídeo — configuração e execução dos testes automatizados | _(link do YouTube)_ |

O professor está cadastrado na organização `RM561940` com acesso **Basic** e como
**administrador** do projeto *PetFlow - Challenge*.

## Parte A — Plano de testes manuais

16 casos de teste de sistema, cadastrados no Azure Boards como work items **Test Case**,
cada um ligado ao item do backlog que valida (aba *Related Work*, link *Tests*). Cada caso
traz o **objetivo**, os **dados de entrada** controlados e a **saída esperada** na
descrição, e o **procedimento** na aba *Steps*, com ação e resultado esperado por passo.

Onde ver no Boards: *Boards > Work items*, filtrando o tipo **Test Case**; a Sprint 4
mostra os casos junto das tarefas de teste (PBI P28).

A execução do plano revelou **dois defeitos**, registrados como **Bug** no Boards, ligados ao
caso que os revelou e corrigidos com testes automatizados:

| Bug | Defeito | Encontrado por |
|---|---|---|
| B01 | Gerar obrigações sem `horizonteDias` devolvia 500 (ORA-01841) | CT10 |
| B02 | Limite de tentativas de login não funcionava no Azure App Service | CT14 |

## Parte B — Automação de testes (Postman / Newman)

A aplicação é testada pela API REST com uma coleção Postman de **5 casos automatizados**
(13 requisições, 28 asserções), executada contra o Azure Web App publicado:

| Caso | O que valida |
|---|---|
| CA01 — Saúde e autenticação | Aplicação no ar e ligada ao Oracle; login do colaborador |
| CA02 — Cadastro e alteração | Tutor pela rota pública, pet vinculado, alteração do pet |
| CA03 — Validação e integridade | 422 com os campos obrigatórios; 409 ao excluir tutor com pet |
| CA04 — Controle de acesso | Login do tutor; 403 na API de gestão; tutor só vê os próprios pets |
| CA05 — Exclusão | Exclusão do pet e do tutor na ordem correta; 404 depois |

Cada execução cria um tutor com e-mail único e apaga tudo o que criou: pode ser repetida
sem preparar dados.

**No Postman:** importe
[`petflow-testes-automatizados.postman_collection.json`](../../tests/postman/petflow-testes-automatizados.postman_collection.json)
e o ambiente
[`petflow-azure.postman_environment.json`](../../tests/postman/petflow-azure.postman_environment.json),
selecione o ambiente *PetFlow - Azure Web App* e use **Run collection**.

**Na linha de comando (Newman):**

A senha do colaborador master não fica no repositório: o ambiente traz
`senhaColaborador` vazio e o valor vem do Key Vault na hora da execução.
No Postman, preencha `senhaColaborador` no ambiente (só localmente, sem
exportar de volta).

```bash
SENHA_MASTER=$(az keyvault secret show --vault-name kv-petflow-s4b-rm561940 \
  --name master-password --query value -o tsv | tr -d '\r')

npx newman run tests/postman/petflow-testes-automatizados.postman_collection.json \
  -e tests/postman/petflow-azure.postman_environment.json \
  --env-var "senhaColaborador=${SENHA_MASTER}" \
  -r cli,junit --reporter-junit-export tests/postman/resultados/newman-junit.xml
```

A coleção é gerada por [`tests/postman/gerar_colecao.py`](../../tests/postman/gerar_colecao.py),
para que os scripts de teste possam ser revisados como código.

> A aplicação aceita no máximo 5 tentativas de login por minuto por IP. Cada execução da
> coleção faz 2 logins; se rodar mais de duas vezes no mesmo minuto, aguarde 1 minuto.
