package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.colaborador.Colaborador;
import br.com.clyvovet.server.colaborador.ColaboradorRepository;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.veterinario.Veterinario;
import br.com.clyvovet.server.veterinario.VeterinarioRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Carrega o usuario das telas a partir do e-mail.
 *
 * <p>So colaborador e veterinario entram: as paginas sao a gestao da clinica
 * — painel de receita, agenda de obrigacoes, prontuario. Tutor continua pelo
 * app, que fala com a API por JWT. Se um tutor tentar entrar, a resposta e a
 * mesma de e-mail inexistente, para nao revelar quais e-mails existem.
 *
 * <p>Como o login antecede o tenant, a busca cruza clinicas — mesma excecao
 * explicita que o {@code AuthService} faz para a API.
 */
@Service
@RequiredArgsConstructor
public class ClinicaUserDetailsService implements UserDetailsService {

    private final ColaboradorRepository colaboradorRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        entityManager.unwrap(Session.class).disableFilter(TenantFilters.TENANT);

        Optional<UserDetails> colaborador = colaboradorRepository.findByEmail(email)
                .map(ClinicaUserDetailsService::doColaborador);
        if (colaborador.isPresent()) {
            return colaborador.get();
        }

        return veterinarioRepository.findByEmail(email)
                .map(ClinicaUserDetailsService::doVeterinario)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Nenhum usuario de clinica com o e-mail informado"));
    }

    private static UserDetails doColaborador(Colaborador c) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(c.getId(), c.getEmail(), c.getNome(),
                        TipoUsuario.COLABORADOR, c.getClinica().getId()),
                c.getSenhaHash());
    }

    private static UserDetails doVeterinario(Veterinario v) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(v.getId(), v.getEmail(), v.getNome(),
                        TipoUsuario.VETERINARIO, v.getClinica().getId()),
                v.getSenhaHash());
    }
}
