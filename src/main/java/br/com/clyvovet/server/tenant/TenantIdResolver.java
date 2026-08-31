package br.com.clyvovet.server.tenant;

import java.util.function.Supplier;

/**
 * Resolve o parametro do filtro de tenant a cada sessao do Hibernate.
 * Instanciado pelo proprio Hibernate — precisa de construtor sem argumentos.
 */
public class TenantIdResolver implements Supplier<Long> {

    @Override
    public Long get() {
        return TenantContext.getOrSemTenant();
    }
}
