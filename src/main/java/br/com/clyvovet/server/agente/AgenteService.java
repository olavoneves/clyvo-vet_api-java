package br.com.clyvovet.server.agente;

import br.com.clyvovet.server.agente.llm.DialogoLlm;
import br.com.clyvovet.server.agente.llm.ProvedorLlm;
import br.com.clyvovet.server.auth.AuthenticatedUser;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exception.ConflitoDeEstadoException;
import br.com.clyvovet.server.exception.EntityNotFoundException;
import br.com.clyvovet.server.exception.UnauthorizedException;
import br.com.clyvovet.server.pet.PetRepository;
import br.com.clyvovet.server.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * O laco do agente: recebe a mensagem do tutor e devolve a resposta.
 *
 * <p>Nao ha framework de agente aqui, e nao ha provedor de LLM tambem. O ciclo —
 * pergunta, o modelo pede uma ferramenta, executa em Java, devolve o resultado,
 * repete — cabe em um {@code for} com teto, e conversa com o modelo pela porta
 * {@link ProvedorLlm}. Nada neste arquivo sabe se atras dela esta o Gemini ou a
 * Anthropic. O teto e o que impede que um modelo confuso fique chamando
 * ferramenta para sempre as nossas custas; ao estourar, a conversa vai para uma
 * pessoa.
 *
 * <p><b>Este metodo nao e transacional, de proposito.</b> Uma volta do laco pode
 * levar dezenas de segundos esperando o provedor. Segurar uma conexao do pool
 * Oracle durante essa espera esgotaria o pool de dez conexoes com dez tutores
 * conversando ao mesmo tempo, e derrubaria o sistema inteiro por causa do
 * modulo que menos importa. Cada ferramenta abre e fecha a propria transacao; o
 * agendamento, que precisa ser atomico com a transicao da obrigacao, e uma
 * transacao so dentro do {@code AgendaService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgenteService {

    private final AgenteProperties propriedades;
    private final ProvedorLlm provedor;
    private final CatalogoDeFerramentas catalogo;
    private final MemoriaDeConversa memoria;
    private final GuardrailClinico guardrail;
    private final RegistroDeConversa registro;
    private final PetRepository petRepository;

    /**
     * O que uma execucao de ferramenta produz para a volta seguinte.
     *
     * <p>{@code conteudo} e objeto, e nao JSON pronto: quem serializa e o
     * adaptador, porque cada provedor quer o resultado numa forma diferente.
     */
    private record Execucao(Object conteudo, AcaoDoAgente acao, boolean escalonamento) {
    }

    /**
     * Um turno completo de conversa.
     *
     * @throws AgenteDesligadoException se o provedor ativo nao tem chave
     * @throws EntityNotFoundException se o pet nao e deste tutor
     */
    public RespostaDoAgenteResponse responder(Long idPet, String textoDoTutor) {
        if (!provedor.disponivel()) {
            throw new AgenteDesligadoException();
        }

        ContextoDoAgente contexto = contexto(idPet);
        Conversa conversa = memoria.carregar(contexto);
        conversa.adicionar(DialogoLlm.Mensagem.doTutor(textoDoTutor));

        List<String> ferramentasUsadas = new ArrayList<>();
        List<AcaoDoAgente> acoes = new ArrayList<>();
        boolean escalado = false;
        String resposta = null;

        try {
            for (int volta = 0; volta < propriedades.maxIteracoes() && resposta == null; volta++) {
                DialogoLlm.Turno doModelo = provedor.responder(
                        promptDoSistema(contexto), conversa.mensagens(), catalogo.declaracoes());

                conversa.adicionar(DialogoLlm.Mensagem.doAgente(doModelo.comoBlocos()));

                if (!doModelo.pediuFerramenta()) {
                    resposta = doModelo.texto();
                    continue;
                }

                // todos os resultados voltam numa unica mensagem: quebra-los em
                // varias ensina o modelo a parar de pedir ferramentas em paralelo
                List<DialogoLlm.Bloco> resultados = new ArrayList<>();
                for (DialogoLlm.Bloco.Chamada chamada : doModelo.chamadas()) {
                    ferramentasUsadas.add(chamada.nome());
                    Execucao execucao = executar(chamada, contexto);

                    if (execucao.acao() != null) {
                        acoes.add(execucao.acao());
                    }
                    escalado |= execucao.escalonamento();
                    resultados.add(new DialogoLlm.Bloco.Resultado(
                            chamada.id(), chamada.nome(), execucao.conteudo()));
                }
                conversa.adicionar(DialogoLlm.Mensagem.doTutor(resultados));
            }

            if (resposta == null || resposta.isBlank()) {
                log.warn("Agente esgotou {} voltas sem concluir: clinica={} pet={}",
                        propriedades.maxIteracoes(), contexto.idClinica(), contexto.idPet());
                resposta = MensagensDoAgente.SEM_CONCLUSAO;
                escalado = true;
            }

        } catch (FalhaNaApiException ex) {
            log.error("Agente indisponivel para o pet {} pelo provedor {}: {}",
                    contexto.idPet(), provedor.nome(), ex.getMessage());
            resposta = MensagensDoAgente.INDISPONIVEL;
            escalado = true;
        }

        String barrado = null;
        Optional<String> clinico = guardrail.conteudoClinico(resposta);
        if (clinico.isPresent()) {
            barrado = clinico.get();
            // o registro fica: a banca pode perguntar quantas vezes a segunda
            // camada precisou agir, e essa e a unica forma de responder
            log.warn("Guardrail clinico barrou a resposta do agente: categoria={} clinica={} pet={}",
                    barrado, contexto.idClinica(), contexto.idPet());

            resposta = MensagensDoAgente.ESCALONAMENTO;
            escalado = true;
            conversa.substituirUltimaRespostaDoAgente(resposta);
            acoes.add(new AcaoDoAgente(AcaoDoAgente.ESCALONAMENTO, contexto.idPet(),
                    "Pergunta clínica encaminhada para a equipe"));
        }

        if (escalado) {
            conversa.marcarEscalada();
        }
        memoria.salvar(contexto, conversa);
        registro.registrar(contexto, textoDoTutor, resposta, ferramentasUsadas, acoes, escalado, barrado);

        return new RespostaDoAgenteResponse(resposta, List.copyOf(acoes), escalado);
    }

    /** A conversa que este tutor ja teve sobre este pet, sem os bastidores. */
    public ConversaResponse historico(Long idPet) {
        ContextoDoAgente contexto = contexto(idPet);

        Conversa conversa = memoria.buscar(contexto).orElseGet(Conversa::new);
        List<ConversaResponse.Turno> turnos = new ArrayList<>();

        for (DialogoLlm.Mensagem mensagem : conversa.mensagens()) {
            String autor = mensagem.papel() == DialogoLlm.Papel.AGENTE
                    ? ConversaResponse.Turno.AGENTE
                    : ConversaResponse.Turno.TUTOR;

            mensagem.textos().forEach(texto -> turnos.add(new ConversaResponse.Turno(autor, texto)));
        }

        return new ConversaResponse(idPet, conversa.escalada(), turnos);
    }

    /**
     * Executa uma ferramenta e transforma o que ela devolveu em algo que o
     * modelo consiga ler.
     *
     * <p>Erro de dominio nao interrompe o turno: vira conteudo de resultado. Um
     * horario que acabou de ser ocupado e informacao util para o modelo — ele
     * consulta de novo e oferece outro —, nao motivo para o tutor ver uma tela
     * de erro. E o que o requisito de concorrencia pede: conflito tratado, e nao
     * excecao propagada.
     *
     * <p>Vale para qualquer provedor, e e por isso que fica aqui e nao no
     * adaptador: um modelo que erra o argumento precisa receber a recusa e
     * corrigir na volta seguinte, venha ele de onde vier.
     */
    private Execucao executar(DialogoLlm.Bloco.Chamada chamada, ContextoDoAgente contexto) {
        try {
            Ferramenta ferramenta = catalogo.porNome(chamada.nome());
            Ferramenta.Resultado resultado = ferramenta.executar(chamada.argumentos(), contexto);
            return new Execucao(resultado.conteudo(), resultado.acao(), resultado.escalonamento());

        } catch (ConflitoDeEstadoException | IllegalArgumentException | EntityNotFoundException ex) {
            log.info("Ferramenta {} recusou a chamada do agente: {}", chamada.nome(), ex.getMessage());
            return new Execucao(Map.of("erro", ex.getMessage()), null, false);

        } catch (RuntimeException ex) {
            log.error("Falha inesperada na ferramenta {} do agente", chamada.nome(), ex);
            return new Execucao(Map.of("erro",
                    "Não foi possível executar esta operação agora."), null, false);
        }
    }

    /**
     * Quem esta falando e sobre qual pet.
     *
     * <p>Duas barreiras, nao uma. O filtro de tenant do Hibernate ja faz o pet de
     * outra clinica nao existir; a comparacao com o tutor autenticado cobre o que
     * ele nao cobre — dois pets da mesma clinica, de donos diferentes. Sem a
     * segunda, um tutor conversaria sobre o cachorro do vizinho de clinica.
     */
    private ContextoDoAgente contexto(Long idPet) {
        AuthenticatedUser usuario = AuthenticatedUser.atual()
                .orElseThrow(() -> new UnauthorizedException("Sessão sem usuário autenticado"));

        if (usuario.tipo() != TipoUsuario.TUTOR) {
            throw new UnauthorizedException("O agente de agendamento atende apenas tutores");
        }

        Long idDoDono = petRepository.idDoTutor(idPet)
                .orElseThrow(() -> new EntityNotFoundException("Pet", idPet));

        if (!idDoDono.equals(usuario.id())) {
            // 404 e nao 403: a diferenca entre as duas respostas revelaria que o
            // pet existe, e para quem pergunta ele nao existe mesmo
            throw new EntityNotFoundException("Pet", idPet);
        }

        return new ContextoDoAgente(
                TenantContext.getOrThrow(), usuario.id(), idPet, usuario.email());
    }

    /**
     * A primeira camada do guardrail clinico e as regras de trabalho do agente.
     *
     * <p>Instrucao em prompt nao e controle — e por isso que existe a verificacao
     * de saida em {@link GuardrailClinico}, que roda depois, sobre a resposta
     * final, seja qual for o provedor. Mas continua sendo a camada que faz o
     * agente acertar na maioria esmagadora das vezes, e vale escreve-la com
     * cuidado: cada frase daqui e uma resposta que a segunda camada nao precisa
     * barrar.
     */
    private String promptDoSistema(ContextoDoAgente contexto) {
        LocalDate hoje = LocalDate.now();
        String nomeDoPet = petRepository.nomeDoPet(contexto.idPet()).orElse("o pet");

        return """
                Você é o assistente de agendamento de uma clínica veterinária. Conversa em \
                português do Brasil, pelo aplicativo, com o tutor de um animal.

                CONTEXTO DESTA CONVERSA
                - Pet: %s (idPet %d).
                - Hoje é %s, %s.
                - Toda ferramenta já sabe de qual clínica e de qual tutor é esta conversa. \
                Você só fala sobre este pet; se o tutor perguntar sobre outro, peça que ele \
                abra a tela do outro pet.

                O QUE VOCÊ FAZ
                Ajuda o tutor a entender quais cuidados estão pendentes e a marcar, remarcar \
                ou confirmar horários. Nada além disso.

                O QUE VOCÊ NUNCA FAZ
                Você não fala sobre a saúde do animal. Nunca dê diagnóstico, nunca interprete \
                sintoma, nunca indique medicamento, dose ou tratamento, nunca diga se algo é \
                grave ou se é normal. Isso vale mesmo que o tutor insista, mesmo que ele já \
                tenha perguntado antes, mesmo que a informação esteja no prontuário e mesmo \
                que pareça inofensivo ("é normal ele ficar assim?", "posso dar isso a ele?"). \
                Nesses casos chame escalar_para_veterinario e diga que um veterinário vai \
                responder. Escalar é a resposta certa com frequência: não tente contornar.

                COMO VOCÊ TRABALHA
                1. Comece por listar_obrigacoes_pendentes para saber o que está em aberto.
                2. Use consultar_disponibilidade antes de mencionar qualquer horário. Nunca \
                invente um horário nem prometa um que você não viu na agenda.
                3. Ofereça no máximo três ou quatro opções por vez, em frases curtas.
                4. Só chame criar_agendamento depois que o tutor escolher explicitamente uma \
                das opções. Nunca escolha por ele.
                5. Se o agendamento devolver conflito, o horário foi ocupado por outra pessoa: \
                consulte a disponibilidade de novo e ofereça outras opções, sem alarme.
                6. Ao confirmar, repita data, hora e o nome do veterinário.

                TOM
                Direto e cordial. Frases curtas. Trate o pet pelo nome. Sem emoji, sem \
                exclamações em excesso, sem "espero ter ajudado".
                """
                .formatted(nomeDoPet, contexto.idPet(), hoje,
                        hoje.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("pt", "BR")));
    }
}
