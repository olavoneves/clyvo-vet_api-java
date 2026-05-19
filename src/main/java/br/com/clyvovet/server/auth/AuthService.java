package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.colaborador.Colaborador;
import br.com.clyvovet.server.colaborador.ColaboradorRepository;
import br.com.clyvovet.server.enums.TipoUsuario;
import br.com.clyvovet.server.exception.UnauthorizedException;
import br.com.clyvovet.server.tutor.Tutor;
import br.com.clyvovet.server.tutor.TutorRepository;
import br.com.clyvovet.server.veterinario.Veterinario;
import br.com.clyvovet.server.veterinario.VeterinarioRepository;
import lombok.RequiredArgsConstructor;
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

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String nome;
        Long id;
        String senhaHash;

        if (request.tipo() == TipoUsuario.TUTOR) {
            Tutor tutor = tutorRepository.findByEmail(request.email())
                    .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
            senhaHash = tutor.getSenhaHash();
            id = tutor.getId();
            nome = tutor.getNome();
        } else if (request.tipo() == TipoUsuario.VETERINARIO) {
            Veterinario vet = veterinarioRepository.findByEmail(request.email())
                    .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
            senhaHash = vet.getSenhaHash();
            id = vet.getId();
            nome = vet.getNome();
        } else {
            Colaborador colaborador = colaboradorRepository.findByEmail(request.email())
                    .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));
            senhaHash = colaborador.getSenhaHash();
            id = colaborador.getId();
            nome = colaborador.getNome();
        }

        if (!passwordEncoder.matches(request.senha(), senhaHash)) {
            throw new UnauthorizedException("Credenciais inválidas");
        }

        String accessToken = jwtService.generateAccessToken(id, request.email(), nome, request.tipo());
        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setTipoUsuario(request.tipo());
        refreshToken.setIdUsuario(id);
        refreshToken.setDtExpiracao(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000));
        refreshToken.setRevogado(false);
        refreshToken.setDtCriacao(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(accessToken, refreshTokenValue, request.tipo(), id, nome, request.email());
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByTokenAndRevogadoFalse(refreshTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido ou revogado"));

        if (stored.getDtExpiracao().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.revokeByToken(refreshTokenValue);
            throw new UnauthorizedException("Refresh token expirado");
        }

        String nome;
        String email;

        if (stored.getTipoUsuario() == TipoUsuario.TUTOR) {
            Tutor tutor = tutorRepository.findById(stored.getIdUsuario())
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
            nome = tutor.getNome();
            email = tutor.getEmail();
        } else if (stored.getTipoUsuario() == TipoUsuario.VETERINARIO) {
            Veterinario vet = veterinarioRepository.findById(stored.getIdUsuario())
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
            nome = vet.getNome();
            email = vet.getEmail();
        } else {
            Colaborador colaborador = colaboradorRepository.findById(stored.getIdUsuario())
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));
            nome = colaborador.getNome();
            email = colaborador.getEmail();
        }

        String newAccessToken = jwtService.generateAccessToken(
                stored.getIdUsuario(), email, nome, stored.getTipoUsuario());

        return new LoginResponse(newAccessToken, refreshTokenValue,
                stored.getTipoUsuario(), stored.getIdUsuario(), nome, email);
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.revokeByToken(refreshTokenValue);
    }
}
