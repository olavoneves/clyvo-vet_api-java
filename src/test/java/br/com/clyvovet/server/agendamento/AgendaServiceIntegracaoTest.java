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
