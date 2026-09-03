package br.com.clyvovet.server.tenant;

/**
 * Clinica dona da requisicao em curso, propagada por thread.
 *
 * <p>Preenchido pelo {@code TenantFilter} a partir do JWT e lido pelo
 * {@link TenantIdResolver} a cada sessao do Hibernate. Nunca e alimentado
 * pelo corpo da requisicao nem por query param.
 */
public final class TenantContext {

    /**
     * Valor usado quando ainda nao ha tenant: login, cadastro publico e bootstrap.
     * Nenhuma clinica real tem esse id — id_clinica e IDENTITY comecando em 1 —
     * entao o filtro resolve para "nenhuma linha", que e a resposta correta para
     * quem ainda nao provou a que clinica pertence.
     */
    public static final Long SEM_TENANT = -1L;

    private static final ThreadLocal<Long> ATUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long idClinica) {
        ATUAL.set(idClinica);
    }

    /** Pode ser null: use quando a ausencia de tenant for um estado legitimo. */
    public static Long get() {
        return ATUAL.get();
    }

    /** Nunca null. Usado pelo resolver do filtro, que exige um valor sempre. */
    public static Long getOrSemTenant() {
        Long idClinica = ATUAL.get();
        return idClinica != null ? idClinica : SEM_TENANT;
    }

    /**
     * Para codigo que so faz sentido dentro de uma requisicao autenticada.
     * Contexto vazio aqui e bug de cadeia de filtros, nao erro de usuario —
     * por isso IllegalStateException e nao uma excecao de dominio.
     */
    public static Long getOrThrow() {
        Long idClinica = ATUAL.get();
        if (idClinica == null) {
            throw new IllegalStateException(
                    "TenantContext vazio: operacao exige requisicao autenticada com claim idClinica");
        }
        return idClinica;
    }

    public static void clear() {
        ATUAL.remove();
    }
}
