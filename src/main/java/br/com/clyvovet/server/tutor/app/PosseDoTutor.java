package br.com.clyvovet.server.tutor.app;

import br.com.clyvovet.server.agendamento.AgendamentoRepository;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.exception.UnauthorizedException;
import br.com.clyvovet.server.pet.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Quem esta pedindo, e o que e dele.
 *
 * <p>E a unica coisa que os controllers do aplicativo do tutor fazem antes de
 * delegar. O tutor sai do {@code SecurityContext}; nenhum metodo daqui recebe
 * id de tutor, porque aceitar um seria reabrir exatamente o buraco que esta
 * superficie existe para fechar.
 *
 * <p>Duas barreiras, nao uma — a mesma leitura que o {@code AgenteService} faz.
 * O filtro de tenant do Hibernate ja faz o pet de outra clinica nao existir; a
 * comparacao com o tutor autenticado cobre o que ele nao cobre: dois pets da
 * mesma clinica, de donos diferentes.
 */
@Component
@RequiredArgsConstructor
public class PosseDoTutor {

    private final PetRepository petRepository;
    private final AgendamentoRepository agendamentoRepository;

    /**
     * O tutor da requisicao em curso.
     *
     * <p>A {@code SecurityConfig} ja restringe {@code /api/tutor/**} a
     * ROLE_TUTOR. A conferencia de tipo aqui e a segunda tranca: uma rota nova
     * registrada na lista errada viraria 401 em vez de vazamento silencioso.
     */
    public AuthenticatedUser autenticado() {
        AuthenticatedUser usuario = AuthenticatedUser.atual()
                .orElseThrow(() -> new UnauthorizedException("Sessão sem usuário autenticado"));

        if (usuario.tipo() != TipoUsuario.TUTOR) {
            throw new UnauthorizedException("Esta superfície atende apenas tutores");
        }
        return usuario;
    }

    /** Atalho para quando so o id importa. */
    public Long id() {
        return autenticado().id();
    }

    /**
     * Recusa o pet que nao e deste tutor.
     *
     * <p>404 e nao 403, de proposito: 403 confirmaria que o pet existe e deixaria
     * enumerar ids pela diferenca entre as duas respostas. Quem nao e dono nao
     * precisa saber a diferenca entre "nao existe" e "nao e seu".
     *
     * @return o proprio id, para encadear na chamada ao service
     * @throws EntityNotFoundException se o pet nao existe, e de outra clinica ou
     *                                 e de outro tutor
     */
    public Long exigirPet(Long idPet) {
        return exigir("Pet", idPet, petRepository.idDoTutor(idPet).orElse(null));
    }

    /**
     * Recusa o agendamento cujo pet nao e deste tutor.
     *
     * @throws EntityNotFoundException nas mesmas tres situacoes de {@link #exigirPet}
     */
    public Long exigirAgendamento(Long idAgendamento) {
        return exigir("Agendamento", idAgendamento,
                agendamentoRepository.idDoTutor(idAgendamento).orElse(null));
    }

    private Long exigir(String recurso, Long id, Long idDoDono) {
        if (idDoDono == null || !idDoDono.equals(id())) {
            throw new EntityNotFoundException(recurso, id);
        }
        return id;
    }
}
