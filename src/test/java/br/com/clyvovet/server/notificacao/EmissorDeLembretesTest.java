package br.com.clyvovet.server.notificacao;

import br.com.clyvovet.server.clinica.Clinica;
import br.com.clyvovet.server.enums.CanalPreferencial;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.obrigacao.Obrigacao;
import br.com.clyvovet.server.pet.Pet;
import br.com.clyvovet.server.protocolo.EtapaProtocolo;
import br.com.clyvovet.server.tutor.Tutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * O emissor de lembretes, sem banco.
 *
 * <p>O que se prova aqui e o contrato do elo que faltava na cadeia de valor:
 * NOTIFICADA emite, o resto nao emite, repeticao nao duplica, e o texto diz ao
 * tutor o que ele precisa para agir — de qual pet, o que e, ate quando.
 */
class EmissorDeLembretesTest {

    private NotificacaoRepository repository;
    private CanalDeNotificacao canal;
    private EmissorDeLembretes emissor;

    @BeforeEach
    void montar() {
        repository = mock(NotificacaoRepository.class);
        canal = mock(CanalDeNotificacao.class);
        given(canal.canal()).willReturn(CanalPreferencial.APP);
        given(repository.save(any(Notificacao.class)))
                .willAnswer(i -> i.getArgument(0));

        emissor = new EmissorDeLembretes(repository, List.of(canal));
    }

    @Test
    @DisplayName("NOTIFICADA emite o lembrete e entrega pelo canal")
    void notificadaEmite() {
        Obrigacao obrigacao = obrigacao(LocalDate.now().plusDays(5));

        emissor.aoTransitar(obrigacao, ObrigacaoStatus.NOTIFICADA);

        ArgumentCaptor<Notificacao> capturada = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository).save(capturada.capture());
        verify(canal).entregar(any(Notificacao.class));

        Notificacao n = capturada.getValue();
        assertThat(n.getTutor().getId()).isEqualTo(7L);
        assertThat(n.getObrigacao()).isSameAs(obrigacao);
        assertThat(n.getCanal()).isEqualTo(CanalPreferencial.APP);
        assertThat(n.naoLida()).as("lembrete nasce por ler").isTrue();
        assertThat(n.getTitulo())
                .as("de qual pet e o que e, que e o que o tutor le na lista")
                .isEqualTo("Thor: V10 2a dose");
    }

    /**
     * As outras transicoes passam pelo mesmo metodo e nao podem virar lembrete.
     * AGENDADA e o caso concreto: e o agente marcando a consulta, e avisar o
     * tutor do que ele acabou de fazer e ruido.
     */
    @Test
    @DisplayName("qualquer outro estado nao emite nada")
    void outrosEstadosNaoEmitem() {
        for (ObrigacaoStatus status : ObrigacaoStatus.values()) {
            if (status == ObrigacaoStatus.NOTIFICADA) {
                continue;
            }
            emissor.aoTransitar(obrigacao(LocalDate.now()), status);
        }

        verify(repository, never()).save(any());
        verify(canal, never()).entregar(any());
    }

    /**
     * A transicao pode se repetir — reprocessamento, correcao manual, o motor
     * rodando de novo. Sem esta guarda cada repeticao empilharia um lembrete
     * duplicado na caixa, e o indice unico da V12 viraria erro em vez de silencio.
     */
    @Test
    @DisplayName("obrigacao que ja tem lembrete no canal nao ganha outro")
    void naoDuplicaLembrete() {
        given(repository.existsByObrigacaoIdAndCanal(42L, CanalPreferencial.APP))
                .willReturn(true);

        emissor.aoTransitar(obrigacao(LocalDate.now().plusDays(3)), ObrigacaoStatus.NOTIFICADA);

        verify(repository, never()).save(any());
        verify(canal, never()).entregar(any());
    }

    @Test
    @DisplayName("prazo vencido e prazo futuro dizem coisas diferentes ao tutor")
    void mensagemMudaComOPrazo() {
        emissor.aoTransitar(obrigacao(LocalDate.now().plusDays(9)), ObrigacaoStatus.NOTIFICADA);
        emissor.aoTransitar(obrigacao(LocalDate.now().minusDays(9)), ObrigacaoStatus.NOTIFICADA);

        ArgumentCaptor<Notificacao> capturadas = ArgumentCaptor.forClass(Notificacao.class);
        verify(repository, org.mockito.Mockito.times(2)).save(capturadas.capture());

        assertThat(capturadas.getAllValues().get(0).getMensagem())
                .as("prazo aberto: quando ainda da")
                .contains("vai até");
        assertThat(capturadas.getAllValues().get(1).getMensagem())
                .as("prazo fechado: dizer que venceu e que ainda da para remarcar")
                .contains("venceu")
                .contains("remarcar");
    }

    private Obrigacao obrigacao(LocalDate janelaFim) {
        Tutor tutor = new Tutor();
        tutor.setId(7L);
        tutor.setNome("Camila");

        Pet pet = new Pet();
        pet.setId(1L);
        pet.setNome("Thor");
        pet.setTutor(tutor);

        EtapaProtocolo etapa = new EtapaProtocolo();
        etapa.setNmEtapa("V10 2a dose");

        Clinica clinica = new Clinica();
        clinica.setId(21L);

        Obrigacao obrigacao = new Obrigacao();
        obrigacao.setId(42L);
        obrigacao.setClinica(clinica);
        obrigacao.setPet(pet);
        obrigacao.setEtapa(etapa);
        obrigacao.setDtPrevista(janelaFim);
        obrigacao.setDtJanelaFim(janelaFim);
        return obrigacao;
    }
}
