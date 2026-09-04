package br.com.clyvovet.server.agendamento;

import br.com.clyvovet.server.enums.AgendamentoStatus;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.obrigacao.MotorProtocolo;
import br.com.clyvovet.server.obrigacao.Obrigacao;
import br.com.clyvovet.server.obrigacao.ObrigacaoRepository;
import br.com.clyvovet.server.veterinario.Veterinario;
import br.com.clyvovet.server.veterinario.VeterinarioRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A agenda como horario livre e horario marcado, nao como linha de tabela.
 *
 * <p>Existe porque o agente precisa de duas operacoes que o
 * {@link AgendamentoService} nao tem: descobrir o que esta livre, e marcar de um
 * jeito que feche o elo com a obrigacao que originou o compromisso. Marcar pelo
 * CRUD deixaria a obrigacao parada em PREVISTA e o painel de receita cego para o
 * agendamento que acabou de acontecer.
 *
 * <p>A disponibilidade nao vem de uma tabela de horarios — nao existe uma. Vem
 * do expediente declarado aqui menos o que ja esta em TB_CLV_AGENDAMENTO. E uma
 * simplificacao consciente: agenda por profissional, com bloqueio e ferias, e
 * outra entrega.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    /** Expediente da clinica na demonstracao. */
    private static final LocalTime ABERTURA = LocalTime.of(9, 0);
    private static final LocalTime FECHAMENTO = LocalTime.of(18, 0);
    private static final LocalTime ALMOCO_INICIO = LocalTime.of(12, 0);
    private static final LocalTime ALMOCO_FIM = LocalTime.of(13, 0);

    private static final Duration DURACAO_DA_CONSULTA = Duration.ofMinutes(30);

    /** Teto de sugestoes: o tutor escolhe entre opcoes, nao le a agenda inteira. */
    private static final int MAX_SUGESTOES = 12;

    /** Mesmo formato de hr_agendamento no banco: VARCHAR2(5). */
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendamentoRepository repository;
    private final VeterinarioRepository veterinarioRepository;
    private final ObrigacaoRepository obrigacaoRepository;
    private final MotorProtocolo motor;
    private final EntityManager entityManager;

    /** Um horario que o tutor pode escolher. */
    public record HorarioLivre(LocalDate data, String hora, Long idVeterinario, String veterinario) {
    }

    /** O que o agente conta ao tutor depois de marcar. */
    public record AgendamentoConfirmado(Long idAgendamento, Long idObrigacao, LocalDate data,
                                        String hora, Long idVeterinario, String veterinario,
                                        String pet) {
    }

    /**
     * Horarios vagos na faixa pedida, do mais proximo ao mais distante.
     *
     * <p>O recorte por clinica vem do filtro de tenant do Hibernate: a lista de
     * veterinarios e a de agendamentos ja chegam so com o que e desta clinica.
     */
    @Transactional(readOnly = true)
    public List<HorarioLivre> horariosLivres(LocalDate de, LocalDate ate) {
        LocalDate inicio = de.isBefore(LocalDate.now()) ? LocalDate.now() : de;
        if (ate.isBefore(inicio)) {
            return List.of();
        }

        List<Veterinario> veterinarios = veterinarioRepository.findAll();
        if (veterinarios.isEmpty()) {
            return List.of();
        }

        Set<String> ocupados = new HashSet<>();
        for (Agendamento a : repository.ocupadosEntre(inicio, ate)) {
            ocupados.add(chave(a.getVeterinario().getId(), a.getDtAgendamento(), a.getHrAgendamento()));
        }

        LocalDateTime agora = LocalDateTime.now();
        List<HorarioLivre> livres = new ArrayList<>();

        for (LocalDate dia = inicio; !dia.isAfter(ate); dia = dia.plusDays(1)) {
            if (fimDeSemana(dia)) {
                continue;
            }
            for (LocalTime hora = ABERTURA; hora.isBefore(FECHAMENTO); hora = hora.plus(DURACAO_DA_CONSULTA)) {
                if (noAlmoco(hora) || !dia.atTime(hora).isAfter(agora)) {
                    continue;
                }
                String texto = hora.format(HORA);
                for (Veterinario veterinario : veterinarios) {
                    if (ocupados.contains(chave(veterinario.getId(), dia, texto))) {
                        continue;
                    }
                    livres.add(new HorarioLivre(dia, texto, veterinario.getId(), veterinario.getNome()));
                    if (livres.size() >= MAX_SUGESTOES) {
                        return livres;
                    }
                }
            }
        }
        return livres;
    }

    /**
     * Marca o compromisso e fecha o elo com a obrigacao que o pediu.
     *
     * <p>A transicao vai pela {@code PR_CLV_TRANSITAR_OBRIGACAO}, nunca por
     * UPDATE: e a procedure que valida o salto de estado, escreve a trilha de
     * auditoria e publica no outbox, tudo na mesma transacao deste metodo.
     *
     * @throws ConflitoDeEstadoException se o horario foi tomado por outro tutor
     */
    @Transactional
    public AgendamentoConfirmado agendar(Long idObrigacao, LocalDateTime quando, Long idVeterinario) {
        Obrigacao obrigacao = obrigacaoRepository.findById(idObrigacao)
                .orElseThrow(() -> new EntityNotFoundException("Obrigacao", idObrigacao));

        Veterinario veterinario = reservar(idVeterinario, quando, null);

        Agendamento agendamento = new Agendamento();
        agendamento.setPet(obrigacao.getPet());
        agendamento.setVeterinario(veterinario);
        agendamento.setDtAgendamento(quando.toLocalDate());
        agendamento.setHrAgendamento(quando.toLocalTime().format(HORA));
        agendamento.setStatus(AgendamentoStatus.CONFIRMADO);
        // origem APP: e o que separa, no painel, o que o produto trouxe do que
        // teria acontecido de qualquer jeito
        agendamento.setCanalOrigem(CanalPreferencial.APP);

        Agendamento salvo = repository.saveAndFlush(agendamento);

        // capturado antes da procedure: depois dela a sessao do Hibernate e
        // descartada e a entidade fica destacada
        AgendamentoConfirmado confirmado = new AgendamentoConfirmado(
                salvo.getId(), idObrigacao, salvo.getDtAgendamento(), salvo.getHrAgendamento(),
                veterinario.getId(), veterinario.getNome(), obrigacao.getPet().getNome());

        motor.transitar(idObrigacao, ObrigacaoStatus.AGENDADA,
                "Agendado pelo agente de agendamento", obrigacao.getDsCorrelationId(),
                salvo.getId(), null, null);

        entityManager.clear();
        return confirmado;
    }

    /** Move um compromisso ja marcado, com a mesma protecao contra choque de horario. */
    @Transactional
    public AgendamentoConfirmado reagendar(Long idAgendamento, LocalDateTime novaDataHora) {
        Agendamento agendamento = repository.buscarComPetEVeterinario(idAgendamento)
                .orElseThrow(() -> new EntityNotFoundException("Agendamento", idAgendamento));

        Veterinario veterinario = reservar(
                agendamento.getVeterinario().getId(), novaDataHora, idAgendamento);

        agendamento.setDtAgendamento(novaDataHora.toLocalDate());
        agendamento.setHrAgendamento(novaDataHora.toLocalTime().format(HORA));
        Agendamento salvo = repository.saveAndFlush(agendamento);

        Obrigacao obrigacao = obrigacaoRepository.findByAgendamentoId(idAgendamento).orElse(null);

        AgendamentoConfirmado confirmado = new AgendamentoConfirmado(
                salvo.getId(), obrigacao == null ? null : obrigacao.getId(),
                salvo.getDtAgendamento(), salvo.getHrAgendamento(),
                veterinario.getId(), veterinario.getNome(), salvo.getPet().getNome());

        if (obrigacao != null) {
            // AGENDADA -> AGENDADA e transicao legal no motor: o remarcar fica
            // registrado na trilha em vez de sumir num UPDATE silencioso
            motor.transitar(obrigacao.getId(), ObrigacaoStatus.AGENDADA,
                    "Reagendado pelo agente de agendamento", obrigacao.getDsCorrelationId(),
                    salvo.getId(), null, null);
            entityManager.clear();
        }
        return confirmado;
    }

    /**
     * Valida o horario e trava o veterinario ate o fim da transacao.
     *
     * @param ignorar agendamento que nao conta como ocupacao — o proprio, num remarcar
     */
    private Veterinario reservar(Long idVeterinario, LocalDateTime quando, Long ignorar) {
        validarHorario(quando);

        Veterinario veterinario = veterinarioRepository.bloquearParaAgenda(idVeterinario)
                .orElseThrow(() -> new EntityNotFoundException("Veterinario", idVeterinario));

        boolean ocupado = repository
                .ocupando(idVeterinario, quando.toLocalDate(), quando.toLocalTime().format(HORA))
                .stream()
                .anyMatch(a -> !a.getId().equals(ignorar));

        if (ocupado) {
            throw new ConflitoDeEstadoException("O horário de "
                    + quando.toLocalTime().format(HORA) + " em " + quando.toLocalDate()
                    + " com " + veterinario.getNome()
                    + " acabou de ser ocupado. Escolha outro horário.");
        }
        return veterinario;
    }

    private void validarHorario(LocalDateTime quando) {
        LocalTime hora = quando.toLocalTime();

        if (!quando.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Não é possível agendar em data ou horário já passado");
        }
        if (fimDeSemana(quando.toLocalDate())) {
            throw new IllegalArgumentException("A clínica não atende aos fins de semana");
        }
        if (hora.isBefore(ABERTURA) || !hora.isBefore(FECHAMENTO) || noAlmoco(hora)) {
            throw new IllegalArgumentException("Horário fora do expediente: "
                    + ABERTURA.format(HORA) + " às " + ALMOCO_INICIO.format(HORA) + " e "
                    + ALMOCO_FIM.format(HORA) + " às " + FECHAMENTO.format(HORA));
        }
        if (hora.getMinute() % DURACAO_DA_CONSULTA.toMinutes() != 0 || hora.getSecond() != 0) {
            throw new IllegalArgumentException("As consultas começam a cada "
                    + DURACAO_DA_CONSULTA.toMinutes() + " minutos");
        }
    }

    private static boolean fimDeSemana(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static boolean noAlmoco(LocalTime hora) {
        return !hora.isBefore(ALMOCO_INICIO) && hora.isBefore(ALMOCO_FIM);
    }

    private static String chave(Long idVeterinario, LocalDate data, String hora) {
        return idVeterinario + "|" + data + "|" + hora;
    }
}
