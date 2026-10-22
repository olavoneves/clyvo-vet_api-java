package br.com.clyvovet.server.tutor.web;

import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.auth.JwtService;
import br.com.clyvovet.server.enums.ObrigacaoStatus;
import br.com.clyvovet.server.enums.ProtocoloCategoria;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.exception.UnauthorizedException;
import br.com.clyvovet.server.obrigacao.ObrigacaoResponse;
import br.com.clyvovet.server.notificacao.NotificacaoService;
import br.com.clyvovet.server.obrigacao.ObrigacaoService;
import br.com.clyvovet.server.pet.PetResponse;
import br.com.clyvovet.server.pet.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Set;

/**
 * A superficie do tutor: o que meu pet precisa, e como marco isso.
 *
 * <p>E a tela que fecha a cadeia de valor. Ate aqui o sistema sabia que uma
 * obrigacao vencia e ate avisava o tutor, mas o tutor nao tinha onde agir — a
 * cadeia parava no lembrete. Esta pagina e o lugar onde ele age.
 *
 * <p>Nao e o aplicativo do tutor. E uma tela, com o que a demonstracao precisa
 * mostrar de ponta a ponta; o app continua sendo outra entrega.
 *
 * <p>Duas rotas para a mesma view: {@code /tutor} escolhe o primeiro pet, para
 * que o login tenha para onde ir, e {@code /tutor/pets/{id}} abre um especifico.
 */
@Controller
@RequiredArgsConstructor
public class TutorWebController {

    /** Pendencias na tela: a lista de um pet cabe folgada, ordenada pela mais proxima. */
    private static final Pageable PENDENTES =
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "dtPrevista"));

    /** Carteirinha: o historico de vacina interessa do mais recente para tras. */
    private static final Pageable CUMPRIDAS =
            PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "dtPrevista"));

    private static final Set<ObrigacaoStatus> EM_ABERTO = Set.of(
            ObrigacaoStatus.PREVISTA, ObrigacaoStatus.NOTIFICADA, ObrigacaoStatus.RESPONDIDA);

    private final PetService petService;
    private final ObrigacaoService obrigacaoService;
    private final JwtService jwtService;
    private final NotificacaoService notificacaoService;

    @GetMapping("/tutor")
    public String inicio(Model model) {
        return pagina(null, model);
    }

    @GetMapping("/tutor/pets/{id}")
    public String pet(@PathVariable Long id, Model model) {
        return pagina(id, model);
    }

    private String pagina(Long idPedido, Model model) {
        AuthenticatedUser tutor = AuthenticatedUser.atual()
                .orElseThrow(() -> new UnauthorizedException("Sessão sem usuário autenticado"));

        List<PetResponse> pets = petService
                .findAtivosDoTutor(tutor.id(), PageRequest.of(0, 50, Sort.by("nome")))
                .getContent();

        model.addAttribute("tutor", tutor);
        model.addAttribute("pets", pets);
        // o badge e do cabecalho, que e o mesmo fragmento das duas telas:
        // ele precisa do numero aqui tambem, e nao so na caixa
        model.addAttribute("naoLidas", notificacaoService.naoLidasDo(tutor.id()));

        PetResponse selecionado = escolher(pets, idPedido);
        if (selecionado == null) {
            // tutor sem pet cadastrado: a tela existe e explica, em vez de dar 404
            model.addAttribute("pet", null);
            return "tutor/pet";
        }

        model.addAttribute("pet", petService.getFichaTecnica(selecionado.id()));
        model.addAttribute("pendentes",
                obrigacaoService.doPetComStatus(selecionado.id(), EM_ABERTO, PENDENTES));
        model.addAttribute("carteirinha", carteirinha(selecionado.id()));

        // A tela e servida pela cadeia de sessao; o agente vive na cadeia da API,
        // que so entende Bearer. O token e emitido aqui para o proprio usuario
        // que ja esta logado — nao concede nada que o cookie dele nao conceda — e
        // faz esta pagina consumir exatamente o mesmo endpoint que o aplicativo
        // vai consumir, em vez de um atalho que so existe na tela.
        model.addAttribute("tokenApi", jwtService.generateAccessToken(
                tutor.id(), tutor.email(), tutor.nome(), tutor.tipo(), tutor.idClinica()));

        return "tutor/pet";
    }

    /**
     * O pet pedido, se for deste tutor; senao o primeiro dele.
     *
     * <p>A lista ja veio filtrada por tutor e por clinica, entao procurar o id
     * dentro dela e a propria verificacao de posse: um id de fora nao esta la.
     */
    private PetResponse escolher(List<PetResponse> pets, Long idPedido) {
        if (idPedido == null) {
            return pets.isEmpty() ? null : pets.getFirst();
        }
        return pets.stream()
                .filter(p -> p.id().equals(idPedido))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Pet", idPedido));
    }

    /**
     * Carteirinha de vacinas: o que ja foi cumprido em protocolo de vacina.
     *
     * <p>Sai das obrigacoes e nao de TB_CLV_APLICACAO_VACINA de proposito. A
     * obrigacao e o que o protocolo prometeu e o que de fato aconteceu, ligada a
     * etapa e a versao vigente na epoca; a aplicacao isolada nao diz de qual
     * esquema vacinal ela fazia parte.
     */
    private List<ObrigacaoResponse> carteirinha(Long idPet) {
        return obrigacaoService
                .doPetComStatus(idPet, Set.of(ObrigacaoStatus.CUMPRIDA), CUMPRIDAS).stream()
                .filter(o -> o.protocoloCategoria() == ProtocoloCategoria.VACINA)
                .toList();
    }
}
