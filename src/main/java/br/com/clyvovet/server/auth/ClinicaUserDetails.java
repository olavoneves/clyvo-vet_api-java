package br.com.clyvovet.server.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal do login por formulario.
 *
 * <p>Carrega o mesmo {@link AuthenticatedUser} que o JWT produz, para que o
 * resto da aplicacao — {@code TenantFilter}, auditoria do motor — nao precise
 * saber por qual porta o usuario entrou. So muda o que o Spring Security exige
 * a mais na autenticacao por senha: username, hash e authorities.
 */
public class ClinicaUserDetails implements UserDetails {

    private final AuthenticatedUser usuario;
    private final String senhaHash;

    public ClinicaUserDetails(AuthenticatedUser usuario, String senhaHash) {
        this.usuario = usuario;
        this.senhaHash = senhaHash;
    }

    public AuthenticatedUser usuario() {
        return usuario;
    }

    /**
     * Nome para exibir no cabecalho das telas.
     *
     * <p>Existe como getter classico de proposito: o {@code sec:authentication}
     * dos templates resolve por SpEL, e SpEL nao enxerga o acessor de um record.
     */
    public String getNomeExibicao() {
        return usuario.nome();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + usuario.tipo().name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return usuario.email();
    }
}
