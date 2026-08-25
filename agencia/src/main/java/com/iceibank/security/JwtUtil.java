package com.iceibank.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Parte F - Autenticacao (JWT).
 *
 * Um JWT e um token assinado digitalmente que carrega informacoes sobre quem esta
 * autenticado. Como a assinatura pode ser verificada matematicamente, o servidor nao
 * precisa consultar um banco de dados a cada requisicao so para saber "quem e esse
 * usuario" - basta validar a assinatura do token.
 */
@Component
public class JwtUtil {

    private final SecretKey chave;
    private final long expiracaoMinutos;

    public JwtUtil(
            @Value("${iceibank.jwt.secret}") String secret,
            @Value("${iceibank.jwt.expiracao-minutos}") long expiracaoMinutos
    ) {
        this.chave = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMinutos = expiracaoMinutos;
    }

    /** Gera um token JWT para o usuario informado, com expiracao definida em application.properties. */
    public String gerarToken(String usuario) {
        Instant agora = Instant.now();
        Instant expira = agora.plusSeconds(expiracaoMinutos * 60);

        return Jwts.builder()
                .subject(usuario)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expira))
                .signWith(chave)
                .compact();
    }

    /** Valida o token e devolve o "subject" (usuario). Lanca JwtException se invalido/expirado. */
    public String validarEExtrairUsuario(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean estaExpirado(ExpiredJwtException e) {
        return e != null;
    }
}
