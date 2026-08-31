package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.colaborador.Colaborador;
import br.com.clyvovet.server.colaborador.ColaboradorRepository;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exception.UnauthorizedException;
import br.com.clyvovet.server.tenant.TenantFilters;
import br.com.clyvovet.server.tutor.Tutor;
import br.com.clyvovet.server.tutor.TutorRepository;
import br.com.clyvovet.server.veterinario.Veterinario;
import br.com.clyvovet.server.veterinario.VeterinarioRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TutorRepository tutorRepository;
    private final VeterinarioRepository veterinarioRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    /** Dados do usuario autenticado, independente do perfil. */
    private record Credenciais(Long id, String nome, String senhaHash, Long idClinica) {
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // e aqui, e so aqui, que a busca precisa cruzar clinicas: quem faz login
        // ainda nao provou a que tenant pertence
        buscarEmTodasAsClinicas();

        Credenciais credenciais = buscarPorEmail(request.tipo(), request.email());

        if (!passwordEncoder.matches(request.senha(), credenciais.senhaHash())) {
            throw new UnauthorizedException("Credenciais inválidas");
        }

        String accessToken = jwtService.generateAccessToken(
                credenciais.id(), request.email(), credenciais.nome(),
                request.tipo(), credenciais.idClinica());

        // COLABORADOR não persiste refresh token — constraint do banco só aceita TUTOR/VETERINARIO
        if (request.tipo() == TipoUsuario.COLABORADOR) {
            return new LoginResponse(accessToken, null, request.tipo(),
                    credenciais.id(), credenciais.nome(), request.email());
        }

        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setTipoUsuario(request.tipo());
        refreshToken.setIdUsuario(credenciais.id());
        refreshToken.setDtExpiracao(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000));
        refreshToken.setRevogado(false);
        refreshToken.setDtCriacao(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, refreshTokenValue, request.tipo(),
                credenciais.id(), credenciais.nome(), request.email());
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByTokenAndRevogadoFalse(refreshTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido ou revogado"));

        if (stored.getDtExpiracao().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.revokeByToken(refreshTokenValue);
            throw new UnauthorizedException("Refresh token expirado");
        }

        // o refresh token identifica o usuario mas nao carrega tenant: mesma excecao do login
        buscarEmTodasAsClinicas();

        String nome;
        String email;
        Long idClinica;

        if (stored.getTipoUsuario() == TipoUsuario.TUTOR) {
            Tutor tutor = tutorRepository.findById(stored.getIdUsuario())
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
            nome = tutor.getNome();
            email = tutor.getEmail();
            idClinica = tutor.getClinica().getId();
        } else if (stored.getTipoUsuario() == TipoUsuario.VETERINARIO) {
            Veterinario vet = veterinarioRepository.findById(stored.getIdUsuario())
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
            nome = vet.getNome();
            email = vet.getEmail();
            idClinica = vet.getClinica().getId();
        } else {
            Colaborador colaborador = colaboradorRepository.findById(stored.getIdUsuario())
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
            nome = colaborador.getNome();
            email = colaborador.getEmail();
            idClinica = colaborador.getClinica().getId();
        }

        String newAccessToken = jwtService.generateAccessToken(
                stored.getIdUsuario(), email, nome, stored.getTipoUsuario(), idClinica);

        return new LoginResponse(newAccessToken, refreshTokenValue,
                stored.getTipoUsuario(), stored.getIdUsuario(), nome, email);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.revokeByToken(refreshTokenValue);
    }

    private Credenciais buscarPorEmail(TipoUsuario tipo, String email) {
        if (tipo == TipoUsuario.TUTOR) {
            Tutor tutor = tutorRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
            return new Credenciais(tutor.getId(), tutor.getNome(), tutor.getSenhaHash(),
                    tutor.getClinica().getId());
        }
        if (tipo == TipoUsuario.VETERINARIO) {
            Veterinario vet = veterinarioRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
            return new Credenciais(vet.getId(), vet.getNome(), vet.getSenhaHash(),
                    vet.getClinica().getId());
        }
        Colaborador colaborador = colaboradorRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
        return new Credenciais(colaborador.getId(), colaborador.getNome(), colaborador.getSenhaHash(),
                colaborador.getClinica().getId());
    }

    /**
     * Desliga o filtro de tenant nesta sessao do Hibernate. O filtro e autoEnabled,
     * entao a excecao precisa ser declarada de forma explicita e local.
     */
    private void buscarEmTodasAsClinicas() {
        entityManager.unwrap(Session.class).disableFilter(TenantFilters.TENANT);
    }
}
