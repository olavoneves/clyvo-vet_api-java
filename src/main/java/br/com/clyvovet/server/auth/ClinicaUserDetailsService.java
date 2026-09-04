package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.colaborador.Colaborador;
import br.com.clyvovet.server.colaborador.ColaboradorRepository;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.tutor.Tutor;
import br.com.clyvovet.server.tutor.TutorRepository;
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
 * <p>Os tres perfis entram, e cada um chega a um lugar diferente: colaborador e
 * veterinario as telas de gestao, tutor a superficie do proprio pet. Quem separa
 * os dois mundos e a autorizacao por papel na {@code SecurityConfig}, nao esta
 * classe — aqui so se descobre quem e a pessoa.
 *
 * <p>A ordem da busca importa pouco para o resultado e muito para o custo: um
 * e-mail so existe em uma das tres tabelas, e tutor e a maior delas, entao vem
 * por ultimo.
 *
 * <p>Como o login antecede o tenant, a busca cruza clinicas — mesma excecao
 * explicita que o {@code AuthService} faz para a API.
 */
@Service
@RequiredArgsConstructor
public class ClinicaUserDetailsService implements UserDetailsService {

    private final ColaboradorRepository colaboradorRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final TutorRepository tutorRepository;
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

        Optional<UserDetails> veterinario = veterinarioRepository.findByEmail(email)
                .map(ClinicaUserDetailsService::doVeterinario);
        if (veterinario.isPresent()) {
            return veterinario.get();
        }

        return tutorRepository.findByEmail(email)
                .map(ClinicaUserDetailsService::doTutor)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Nenhum usuario com o e-mail informado"));
    }

    private static UserDetails doColaborador(Colaborador c) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(c.getId(), c.getEmail(), c.getNome(),
                        TipoUsuario.COLABORADOR, c.getClinica().getId()),
                c.getSenhaHash());
    }

    private static UserDetails doTutor(Tutor t) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(t.getId(), t.getEmail(), t.getNome(),
                        TipoUsuario.TUTOR, t.getClinica().getId()),
                t.getSenhaHash());
    }

    private static UserDetails doVeterinario(Veterinario v) {
        return new ClinicaUserDetails(
                new AuthenticatedUser(v.getId(), v.getEmail(), v.getNome(),
                        TipoUsuario.VETERINARIO, v.getClinica().getId()),
                v.getSenhaHash());
    }
}
