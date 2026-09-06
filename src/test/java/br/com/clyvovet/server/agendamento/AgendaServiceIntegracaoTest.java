package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O elo que fecha a cadeia: agendar cria o compromisso e devolve a obrigacao
 * para o trilho.
 *
 * <p>E o unico ponto do agente que precisa do banco de verdade. As duas escritas
 * — a linha em TB_CLV_AGENDAMENTO e a transicao para AGENDADA pela procedure —
 * tem que acontecer juntas ou nao acontecer; com mock nao ha o que provar,
 * porque e exatamente a atomicidade entre elas que esta sob teste.
 *
 * <p>A fixture sai dos dados que existirem: uma obrigacao pendente qualquer e o
 * primeiro horario que a propria consulta de disponibilidade oferecer. Fixar
 * data e veterinario transformaria o teste num detector de calendario.
 *
 * <p>Roda dentro da transacao do teste e e desfeito ao final: as procedures nao
 * dao COMMIT proprio, entao o rollback alcanca o que elas escreveram.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+",
        disabledReason = "exige o Oracle com o motor de protocolo instalado")
class AgendaServiceIntegracaoTest {

    /** Janela varrida em busca de vaga: tres semanas cobrem qualquer feriado. */
    private static final int DIAS_DE_BUSCA = 21;

    /** Marca as linhas que este teste fabrica, para reencontra-las sem ambiguidade. */
    private static final String CORRELACAO_DO_TESTE = "teste-agenda-vencida";

    @Autowired
    private AgendaService agendaService;

    @Autowired
    private EntityManager entityManager;

    private Long clinica;

    @BeforeEach
    void escolherClinica() {
        List<Long> clinicas = entityManager
                .createNativeQuery("select id_clinica from TB_CLV_CLINICA order by id_clinica", Long.class)
                .getResultList();
        Assumptions.assumeFalse(clinicas.isEmpty(), "nenhuma clinica cadastrada");

        clinica = clinicas.get(0);
        TenantContext.set(clinica);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("agendar grava TB_CLV_AGENDAMENTO e transiciona a obrigacao para AGENDADA")
    void agendarFechaOElo() {
        Long obrigacao = umaObrigacaoPendente();
        AgendaService.HorarioLivre vaga = primeiraVaga();

        AgendaService.AgendamentoConfirmado confirmado = agendaService.agendar(
                obrigacao, quando(vaga), vaga.idVeterinario());

        assertThat(confirmado.idAgendamento()).isNotNull();
        assertThat(confirmado.hora()).isEqualTo(vaga.hora());

        // a linha nasce com o canal que separa, no painel, o que o produto trouxe
        assertThat(contar("""
                select count(*) from TB_CLV_AGENDAMENTO
                 where id_agendamento = %d
                   and ds_canal_origem = 'APP'
                   and ds_status = 'CONFIRMADO'
                   and id_veterinario = %d
                """.formatted(confirmado.idAgendamento(), vaga.idVeterinario())))
                .isEqualTo(1);

        // a obrigacao voltou para o trilho, e apontando para este agendamento
        assertThat(contar("""
                select count(*) from TB_CLV_OBRIGACAO
                 where id_obrigacao = %d and ds_status = 'AGENDADA' and id_agendamento = %d
                """.formatted(obrigacao, confirmado.idAgendamento())))
                .as("a obrigacao precisa terminar em AGENDADA apontando para o agendamento criado")
                .isEqualTo(1);

        // quem mudou o status foi a procedure: a trilha de transicao prova isso
        assertThat(contar("""
                select count(*) from TB_CLV_OBRIGACAO_TRANSICAO
                 where id_obrigacao = %d and ds_status_para = 'AGENDADA'
                """.formatted(obrigacao)))
                .as("transicao sem trilha significa UPDATE direto, contornando o motor")
                .isPositive();
    }

    @Test
    @DisplayName("o horario marcado deixa de ser oferecido")
    void horarioOcupadoSaiDaDisponibilidade() {
        Long obrigacao = umaObrigacaoPendente();
        AgendaService.HorarioLivre vaga = primeiraVaga();

        agendaService.agendar(obrigacao, quando(vaga), vaga.idVeterinario());
        entityManager.flush();

        assertThat(vagas())
                .as("a vaga recem-ocupada nao pode continuar na lista")
                .noneMatch(livre -> livre.data().equals(vaga.data())
                        && livre.hora().equals(vaga.hora())
                        && livre.idVeterinario().equals(vaga.idVeterinario()));
    }

    /**
     * O mesmo horario nao pode ser vendido duas vezes.
     *
     * <p>O teste e sequencial, entao o que ele exercita e a revalidacao dentro da
     * transacao — nao a corrida de verdade. A corrida e barrada um passo antes,
     * pelo {@code SELECT ... FOR UPDATE} na linha do veterinario, que serializa
     * duas transacoes concorrentes ate esta mesma checagem. Provar isso exigiria
     * duas conexoes e um ponto de sincronizacao; o que da para provar aqui e que
     * a checagem existe e recusa.
     */
    @Test
    @DisplayName("marcar de novo no mesmo horario devolve conflito tratado")
    void horarioDuplicadoDaConflito() {
        Long obrigacao = umaObrigacaoPendente();
        AgendaService.HorarioLivre vaga = primeiraVaga();

        agendaService.agendar(obrigacao, quando(vaga), vaga.idVeterinario());
        entityManager.flush();

        assertThatThrownBy(() -> agendaService.agendar(obrigacao, quando(vaga), vaga.idVeterinario()))
                .isInstanceOf(ConflitoDeEstadoException.class)
                .hasMessageContaining("Escolha outro horário");
    }

    /**
     * A consulta que a agenda operacional usa, contra o banco de verdade.
     *
     * <p>O que se prova aqui e que ela <b>executa</b>: e um join de entidade com
     * clausula ON, por um lado da relacao que a {@code Agendamento} nao mapeia —
     * quem tem a chave e {@code Obrigacao.agendamento}. Os testes da pagina
     * rodam sobre mock e passariam com um JPQL que nem compila no Oracle.
     *
     * <p>E prova o left join: marcar pelo agente cria a linha com obrigacao
     * atras, e ela tem que voltar com etapa e protocolo preenchidos, e nao nulos.
     */
    @Test
    @DisplayName("a agenda devolve o compromisso recem-marcado, com a obrigacao atras")
    void agendaTrazOCompromissoComAObrigacao() {
        Long obrigacao = umaObrigacaoPendente();
        AgendaService.HorarioLivre vaga = primeiraVaga();

        AgendaService.AgendamentoConfirmado feito =
                agendaService.agendar(obrigacao, quando(vaga), vaga.idVeterinario());
        entityManager.flush();
        entityManager.clear();

        List<CompromissoDaAgenda> agenda =
                agendaService.compromissosEntre(vaga.data(), vaga.data());

        assertThat(agenda)
                .as("o compromisso marcado tem que aparecer no dia em que acontece")
                .anySatisfy(c -> {
                    assertThat(c.idAgendamento()).isEqualTo(feito.idAgendamento());
                    assertThat(c.data()).isEqualTo(vaga.data());
                    assertThat(c.hora()).isEqualTo(vaga.hora());
                    assertThat(c.veioDeObrigacao())
                            .as("veio do motor: a etapa e o protocolo nao podem vir nulos")
                            .isTrue();
                    assertThat(c.etapaNome()).isNotBlank();
                    assertThat(c.protocoloNome()).isNotBlank();
                });
    }

    /**
     * Obrigacao vencida remarcada para frente aparece marcada como atraso.
     *
     * <p>E o caso que motivou trocar o criterio da tela: pela data prevista ela
     * some, porque o vencimento ja passou. Pela data do compromisso ela aparece,
     * que e a receita recuperada ficando visivel.
     */
    @Test
    @DisplayName("compromisso de obrigacao vencida vem marcado como atraso")
    void compromissoDeObrigacaoVencidaVemMarcado() {
        Long vencida = umaObrigacaoVencida();

        AgendaService.HorarioLivre vaga = primeiraVaga();
        agendaService.agendar(vencida, quando(vaga), vaga.idVeterinario());
        entityManager.flush();
        entityManager.clear();

        assertThat(agendaService.compromissosEntre(vaga.data(), vaga.data()))
                .filteredOn(CompromissoDaAgenda::recuperaAtraso)
                .as("a obrigacao vencida remarcada para frente tem que vir marcada")
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.diasDeAtraso()).isPositive());
    }

    @Test
    @DisplayName("fim de semana e horario fora do expediente sao recusados")
    void horarioForaDoExpedienteERecusado() {
        Long obrigacao = umaObrigacaoPendente();
        AgendaService.HorarioLivre vaga = primeiraVaga();
        Long veterinario = vaga.idVeterinario();

        LocalDate sabado = proximoSabado();

        assertThatThrownBy(() -> agendaService.agendar(
                obrigacao, sabado.atTime(10, 0), veterinario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fins de semana");

        assertThatThrownBy(() -> agendaService.agendar(
                obrigacao, vaga.data().atTime(21, 0), veterinario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expediente");

        assertThatThrownBy(() -> agendaService.agendar(
                obrigacao, vaga.data().atTime(10, 17), veterinario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 minutos");
    }

    // ---------- fixture ----------

    private List<AgendaService.HorarioLivre> vagas() {
        LocalDate hoje = LocalDate.now();
        return agendaService.horariosLivres(hoje, hoje.plusDays(DIAS_DE_BUSCA));
    }

    private AgendaService.HorarioLivre primeiraVaga() {
        List<AgendaService.HorarioLivre> livres = vagas();
        Assumptions.assumeFalse(livres.isEmpty(),
                "a clinica nao tem veterinario cadastrado ou esta com a agenda cheia");
        return livres.getFirst();
    }

    private LocalDateTime quando(AgendaService.HorarioLivre vaga) {
        return vaga.data().atTime(LocalTime.parse(vaga.hora(), AgendaService.HORA));
    }

    private Long umaObrigacaoPendente() {
        List<Long> pendentes = entityManager.createNativeQuery("""
                        select id_obrigacao from TB_CLV_OBRIGACAO
                         where id_clinica = :clinica
                           and ds_status in ('PREVISTA','NOTIFICADA','RESPONDIDA')
                         order by id_obrigacao
                        """, Long.class)
                .setParameter("clinica", clinica)
                .setMaxResults(1)
                .getResultList();

        Assumptions.assumeFalse(pendentes.isEmpty(),
                "a clinica nao tem obrigacao pendente: rode o motor de protocolo antes");
        return pendentes.getFirst();
    }

    /**
     * Uma obrigacao vencida criada aqui, e nao procurada na base.
     *
     * <p>Antes o teste varria {@code TB_CLV_OBRIGACAO} atras de alguma linha com
     * {@code dt_prevista} no passado e desistia por {@code assumeFalse} quando
     * nao achava. Isso o tornava um teste que so existia em base ja envelhecida:
     * num banco recem-semeado — todo ambiente novo, e o container local depois de
     * um reseed — ele passava a vida inteira em verde-cinza, sem nunca ter
     * exercitado nada. Skip silencioso e a mesma coisa que nao ter o teste, com a
     * agravante de parecer que se tem.
     *
     * <p>O vencimento e o unico dado que este caso precisa, entao ele e o unico
     * fabricado: 30 dias no passado, sobre pet, versao e etapa que ja existem no
     * catalogo clinico. Roda dentro da transacao do teste, entao nada sobra.
     */
    private Long umaObrigacaoVencida() {
        Long modelo = idNativo("""
                select min(id_obrigacao) from TB_CLV_OBRIGACAO where id_clinica = %d
                """.formatted(clinica));
        Assumptions.assumeTrue(modelo != null,
                "a clinica precisa de ao menos uma obrigacao para servir de molde");

        // copia de uma linha existente, com o vencimento no passado e o status de
        // volta ao inicio do trilho: e o unico jeito de garantir pet, versao e
        // etapa coerentes entre si sem recriar o catalogo inteiro no teste.
        entityManager.createNativeQuery("""
                insert into TB_CLV_OBRIGACAO
                       (id_clinica, id_pet, id_versao_protocolo, id_etapa,
                        dt_prevista, dt_janela_inicio, dt_janela_fim,
                        ds_status, ds_correlation_id, fl_grupo_controle)
                select id_clinica, id_pet, id_versao_protocolo, id_etapa,
                       TRUNC(SYSDATE) - 30, TRUNC(SYSDATE) - 37, TRUNC(SYSDATE) - 23,
                       'PREVISTA', '%s', 'N'
                  from TB_CLV_OBRIGACAO where id_obrigacao = %d
                """.formatted(CORRELACAO_DO_TESTE, modelo)).executeUpdate();
        entityManager.flush();

        return idNativo("""
                select max(id_obrigacao) from TB_CLV_OBRIGACAO
                 where ds_correlation_id = '%s'
                """.formatted(CORRELACAO_DO_TESTE));
    }

    private Long idNativo(String sql) {
        Object resultado = entityManager.createNativeQuery(sql).getSingleResult();
        return resultado == null ? null : ((Number) resultado).longValue();
    }

    private static LocalDate proximoSabado() {
        LocalDate dia = LocalDate.now().plusDays(1);
        while (dia.getDayOfWeek() != java.time.DayOfWeek.SATURDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    private int contar(String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).intValue();
    }
}
