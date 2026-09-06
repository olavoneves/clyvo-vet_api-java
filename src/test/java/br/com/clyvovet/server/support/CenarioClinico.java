package br.com.clyvovet.server.support;

import jakarta.persistence.EntityManager;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Constroi o cenario que o teste precisa, em vez de procura-lo na base.
 *
 * <p>Existe por causa de um padrao que a suite tinha e que custou uma rodada
 * inteira para ser visto: varios testes de integracao comecavam procurando um
 * dado — uma clinica, um pet de outra clinica, uma obrigacao PREVISTA — e
 * chamavam {@code Assumptions.assumeFalse} quando nao achavam. Nunca falhavam, e
 * numa base recem-semeada tambem nunca exercitavam nada. Contavam como cobertura
 * sendo o equivalente a nao existir, com o agravante de parecer que existiam.
 *
 * <p>Aqui cada teste fabrica o que vai afirmar. Tudo roda <b>dentro da transacao
 * do teste</b> e some no rollback — inclusive o que as procedures escrevem, que
 * nao dao COMMIT proprio.
 *
 * <p><b>Inserts nativos, e nao repositorios.</b> Duas razoes: o {@code @Filter}
 * de tenant nao pode atrapalhar a montagem de um cenario que muitas vezes e
 * justamente sobre duas clinicas; e a fixture nao deve depender das mesmas
 * classes que o teste esta exercitando — um mapeamento quebrado tem que quebrar o
 * teste, nao a preparacao dele.
 *
 * <p>O catalogo clinico (especie, raca, protocolo, etapa) <b>nao</b> e fabricado:
 * ele e global, nasce das migrations V7/V9 e existe em qualquer schema migrado.
 * Recria-lo por teste seria recriar o produto.
 */
public final class CenarioClinico {

    /**
     * Sufixo unico por objeto criado.
     *
     * <p>CNPJ, e-mail, CRMV e microchip tem indice unico. Dois testes da mesma
     * execucao — ou o mesmo teste rodando duas vezes contra uma base que nao
     * limpou — colidiriam num valor fixo. O contador e estatico porque a
     * unicidade tem que valer para a JVM inteira, nao para a instancia.
     */
    private static final AtomicLong SEQUENCIA = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    private final EntityManager entityManager;

    public CenarioClinico(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Um identificador que nao colide com nada que ja exista na base. */
    public static String sufixo() {
        return String.valueOf(SEQUENCIA.incrementAndGet());
    }

    // ---------- raiz do tenant ----------

    /** Uma clinica nova, com ticket medio declarado zero (o real sai das consultas). */
    public Long novaClinica(String nome) {
        String id = sufixo();
        executar("""
                insert into TB_CLV_CLINICA
                       (nm_clinica, ds_cnpj, ds_logradouro, nm_cidade, sg_estado, nr_ticket_medio)
                values ('%s', '%s', 'Rua do Teste', 'Sao Paulo', 'SP', 0)
                """.formatted(nome + " " + id, cnpj(id)));

        return id("""
                select max(id_clinica) from TB_CLV_CLINICA where ds_cnpj = '%s'
                """.formatted(cnpj(id)));
    }

    public Long novoTutor(Long clinica) {
        String id = sufixo();
        String email = "tutor" + id + "@teste.local";
        executar("""
                insert into TB_CLV_TUTOR (nm_tutor, ds_email, ds_senha_hash, id_clinica)
                values ('Tutor de teste %s', '%s', '$2a$10$naoUsadaEmTeste000000000000000000000000000000000000', %d)
                """.formatted(id, email, clinica));

        return id("select max(id_tutor) from TB_CLV_TUTOR where ds_email = '%s'".formatted(email));
    }

    public Long novoVeterinario(Long clinica) {
        String id = sufixo();
        String email = "vet" + id + "@teste.local";
        executar("""
                insert into TB_CLV_VETERINARIO
                       (nm_veterinario, nr_crmv, ds_email, ds_senha_hash, id_clinica)
                values ('Vet de teste %s', 'TST-%s', '%s',
                        '$2a$10$naoUsadaEmTeste000000000000000000000000000000000000', %d)
                """.formatted(id, id, email, clinica));

        return id("select max(id_veterinario) from TB_CLV_VETERINARIO where ds_email = '%s'"
                .formatted(email));
    }

    /**
     * Um pet canino adulto, com tutor proprio.
     *
     * <p>A raca vem do catalogo global. Adulto de proposito: filhote casaria com
     * as regras de ativacao da serie vacinal e o motor geraria obrigacoes por
     * conta propria em qualquer gatilho, o que atrapalha os testes que contam
     * quantas obrigacoes existem.
     */
    public Long novoPet(Long clinica, Long tutor) {
        return novoPet(clinica, tutor, 48);
    }

    /**
     * Um pet canino da idade pedida.
     *
     * <p>A idade nao e detalhe: as regras de ativacao do catalogo olham para ela.
     * Um filhote entre 1,5 e 4 meses casa com {@code CAN_V10_SERIE} e faz o motor
     * ter obrigatoriamente o que gerar; um adulto de 48 meses nao casa com nada
     * que dispare sozinho, que e o que os testes de contagem precisam.
     */
    public Long novoPet(Long clinica, Long tutor, int idadeEmMeses) {
        String id = sufixo();
        Long raca = id("""
                select min(r.id_raca) from TB_CLV_RACA r
                  join TB_CLV_ESPECIE e on e.id_especie = r.id_especie
                 where upper(e.nm_especie) = 'CANINA'
                """);
        if (raca == null) {
            throw new IllegalStateException(
                    "catalogo sem raca canina: o schema nao tem a V9 aplicada");
        }

        executar("""
                insert into TB_CLV_PET (nm_pet, dt_nascimento, ds_sexo, ds_porte, ds_status_pet,
                                        nr_microchip, id_tutor, id_raca, id_clinica)
                values ('Pet de teste %s', ADD_MONTHS(TRUNC(SYSDATE), -%d), 'M', 'MEDIO', 'ATIVO',
                        '%s', %d, %d, %d)
                """.formatted(id, idadeEmMeses, microchip(id), tutor, raca, clinica));

        return id("select max(id_pet) from TB_CLV_PET where nr_microchip = '%s'"
                .formatted(microchip(id)));
    }

    /** Clinica + tutor + veterinario + pet, que e o minimo para quase todo cenario. */
    public Cenario clinicaCompleta(String nome) {
        Long clinica = novaClinica(nome);
        Long tutor = novoTutor(clinica);
        Long veterinario = novoVeterinario(clinica);
        Long pet = novoPet(clinica, tutor);
        entityManager.flush();
        return new Cenario(clinica, tutor, veterinario, pet);
    }

    public record Cenario(Long clinica, Long tutor, Long veterinario, Long pet) {
    }

    // ---------- obrigacoes ----------

    /**
     * Uma obrigacao no estado e no vencimento que o teste pedir.
     *
     * <p>A etapa sai do catalogo global — e preciso que etapa, versao e protocolo
     * sejam coerentes entre si, e recria-los aqui seria recriar o catalogo. O que
     * o teste controla e o que ele esta afirmando: pet, estado, vencimento e
     * grupo.
     *
     * @param diasAteVencer negativo para obrigacao ja vencida
     * @param grupoControle {@code true} sorteia a obrigacao para o controle
     */
    public Long novaObrigacao(Long clinica, Long pet, String status,
                              int diasAteVencer, boolean grupoControle) {
        String correlacao = "teste-" + sufixo();
        Long etapa = id("""
                select min(id_etapa) from TB_CLV_ETAPA_PROTOCOLO e
                  join TB_CLV_VERSAO_PROTOCOLO v on v.id_versao_protocolo = e.id_versao_protocolo
                 where v.ds_status = 'VIGENTE'
                """);
        if (etapa == null) {
            throw new IllegalStateException(
                    "catalogo sem etapa vigente: o schema nao tem a V9 aplicada");
        }

        executar("""
                insert into TB_CLV_OBRIGACAO
                       (id_clinica, id_pet, id_versao_protocolo, id_etapa,
                        dt_prevista, dt_janela_inicio, dt_janela_fim,
                        ds_status, ds_correlation_id, fl_grupo_controle, nr_valor_estimado)
                select %d, %d, e.id_versao_protocolo, e.id_etapa,
                       TRUNC(SYSDATE) + %d, TRUNC(SYSDATE) + %d - 7, TRUNC(SYSDATE) + %d + 30,
                       '%s', '%s', '%s', 200
                  from TB_CLV_ETAPA_PROTOCOLO e where e.id_etapa = %d
                """.formatted(clinica, pet, diasAteVencer, diasAteVencer, diasAteVencer,
                status, correlacao, grupoControle ? "S" : "N", etapa));
        entityManager.flush();

        return id("select max(id_obrigacao) from TB_CLV_OBRIGACAO where ds_correlation_id = '%s'"
                .formatted(correlacao));
    }

    /** Uma consulta realizada, que e o gatilho que o motor de protocolo escuta. */
    public Long novaConsulta(Long pet, Long veterinario) {
        String id = sufixo();
        executar("""
                insert into TB_CLV_CONSULTA (dt_consulta, ds_motivo, ds_status, nr_valor,
                                             id_pet, id_veterinario)
                values (TRUNC(SYSDATE), 'Consulta do teste %s', 'REALIZADA', 180, %d, %d)
                """.formatted(id, pet, veterinario));
        entityManager.flush();

        return id("""
                select max(id_consulta) from TB_CLV_CONSULTA
                 where id_pet = %d and ds_motivo = 'Consulta do teste %s'
                """.formatted(pet, id));
    }

    /** Varias obrigacoes iguais, para quando o teste precisa de volume. */
    public void varias(Long clinica, Long pet, String status,
                       int diasAteVencer, boolean grupoControle, int quantas) {
        for (int i = 0; i < quantas; i++) {
            novaObrigacao(clinica, pet, status, diasAteVencer, grupoControle);
        }
    }

    // ---------- utilitarios ----------

    private String cnpj(String id) {
        // 14 digitos, como a coluna exige; o prefixo 99 nao colide com o seed
        return ("99" + id + "000000000000").substring(0, 14);
    }

    private String microchip(String id) {
        return ("77" + id + "0000000000").substring(0, 15);
    }

    private void executar(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    public Long id(String sql) {
        Object resultado = entityManager.createNativeQuery(sql).getSingleResult();
        return resultado == null ? null : ((Number) resultado).longValue();
    }
}
