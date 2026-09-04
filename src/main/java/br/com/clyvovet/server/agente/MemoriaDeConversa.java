package br.com.clyvovet.server.agente;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Onde a conversa em andamento vive entre uma mensagem e a proxima.
 *
 * <p>Caffeine, nao Redis. O spec previa Redis quando configurado, e a estrutura
 * daqui aceita essa troca — a chave ja e uma string com tenant embutido e o
 * acesso passa por {@code carregar}/{@code salvar}. Mas subir Redis so para
 * isto contraria a decisao que este projeto ja tomou em {@code CacheConfig}: a
 * demonstracao nao pode depender de um servico externo de pe. O custo dessa
 * escolha e conhecido e aceito — com mais de uma instancia, o tutor que cair em
 * outra perde o fio da conversa.
 *
 * <p>O que <b>nao</b> se perde e o historico: cada turno e gravado em
 * TB_CLV_OUTBOX_EVENT na mesma transacao. Isto aqui e memoria de trabalho, nao
 * registro.
 *
 * <p>A chave comeca pela clinica. Nao por elegancia: o id do tutor e IDENTITY
 * por tabela, entao o tutor 7 da clinica A e o tutor 7 da clinica B existem ao
 * mesmo tempo, e uma chave sem tenant entregaria a conversa de um ao outro.
 */
@Component
public class MemoriaDeConversa {

    /** Conversas simultaneas retidas. Estourado o teto, sai a menos usada. */
    private static final int TETO_DE_CONVERSAS = 10_000;

    private final Cache<String, Conversa> conversas;

    public MemoriaDeConversa(AgenteProperties propriedades) {
        this.conversas = Caffeine.newBuilder()
                // depois de expirar, a proxima mensagem do tutor comeca do zero:
                // e o comportamento certo para uma conversa de ontem
                .expireAfterAccess(propriedades.validadeDaConversa())
                .maximumSize(TETO_DE_CONVERSAS)
                .build();
    }

    public Optional<Conversa> buscar(ContextoDoAgente contexto) {
        return Optional.ofNullable(conversas.getIfPresent(chave(contexto)));
    }

    /** A conversa existente, ou uma nova ainda nao guardada. */
    public Conversa carregar(ContextoDoAgente contexto) {
        return buscar(contexto).orElseGet(Conversa::new);
    }

    public void salvar(ContextoDoAgente contexto, Conversa conversa) {
        conversa.podar();
        conversas.put(chave(contexto), conversa);
    }

    private static String chave(ContextoDoAgente contexto) {
        return contexto.idClinica() + ":" + contexto.idTutor() + ":" + contexto.idPet();
    }
}
