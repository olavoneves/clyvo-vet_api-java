package br.com.clyvovet.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenAndRevogadoFalse(String token);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.token = :token")
    void revokeByToken(String token);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.dtExpiracao < :now")
    void deleteExpired(LocalDateTime now);
}
