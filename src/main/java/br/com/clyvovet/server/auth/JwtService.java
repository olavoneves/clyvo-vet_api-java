package br.com.clyvovet.server.auth;

import br.com.clyvovet.server.enums.TipoUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    /** Claim do tenant. Unica origem do id_clinica em requisicao autenticada. */
    public static final String CLAIM_ID_CLINICA = "idClinica";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    public String generateAccessToken(Long id, String email, String nome, TipoUsuario tipo, Long idClinica) {
        return Jwts.builder()
                .subject(email)
                .claim("id", id)
                .claim("nome", nome)
                .claim("tipo", tipo.name())
                .claim(CLAIM_ID_CLINICA, idClinica)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
