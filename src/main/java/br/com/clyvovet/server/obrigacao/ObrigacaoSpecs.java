package br.com.clyvovet.server.obrigacao;

import br.com.clyvovet.server.enums.ObrigacaoStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Filtros opcionais da listagem. Specification em vez de "(:x is null or ...)"
 * em HQL: aqui o predicado vira uma conjuncao vazia quando o filtro nao veio,
 * o que evita o bind de null sem tipo que o Oracle rejeita.
 *
 * <p>Cada metodo devolve uma Specification nao nula — allOf recusa elementos
 * nulos — e resolve o filtro dentro do lambda, ja com o CriteriaBuilder em maos.
 *
 * <p>Nao ha filtro por clinica: quem faz isso e o filtro de tenant do Hibernate.
 */
public final class ObrigacaoSpecs {

    private ObrigacaoSpecs() {
    }

    public static Specification<Obrigacao> comStatus(ObrigacaoStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("dsStatus"), status);
    }

    /**
     * Varios status de uma vez. Usado pelas pendencias do tutor, que sao tres
     * estados da mesma coisa: prevista, avisada e respondida sem agendar.
     */
    public static Specification<Obrigacao> comStatusEm(Collection<ObrigacaoStatus> status) {
        return (root, query, cb) -> status == null || status.isEmpty()
                ? cb.conjunction()
                : root.get("dsStatus").in(status);
    }

    public static Specification<Obrigacao> previstaDe(LocalDate de) {
        return (root, query, cb) -> de == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("dtPrevista"), de);
    }

    public static Specification<Obrigacao> previstaAte(LocalDate ate) {
        return (root, query, cb) -> ate == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("dtPrevista"), ate);
    }

    public static Specification<Obrigacao> doPet(Long petId) {
        return (root, query, cb) -> petId == null
                ? cb.conjunction()
                : cb.equal(root.get("pet").get("id"), petId);
    }

    public static Specification<Obrigacao> filtro(ObrigacaoStatus status, LocalDate de,
                                                  LocalDate ate, Long petId) {
        return Specification.allOf(
                comStatus(status), previstaDe(de), previstaAte(ate), doPet(petId));
    }
}
