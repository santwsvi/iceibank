package com.iceibank.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que roda em toda requisicao: le o cabecalho "Authorization: Bearer <token>",
 * valida o JWT (assinatura + expiracao) e, se valido, autentica a requisicao no
 * contexto de seguranca do Spring. Se o token estiver ausente ou invalido, a
 * requisicao segue sem autenticacao - e o SecurityConfig quem decide, para cada
 * rota, se autenticacao e obrigatoria (e nesse caso o Spring Security devolve 401).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                String usuario = jwtUtil.validarEExtrairUsuario(token);
                var auth = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                // Token presente mas invalido/expirado: nao autentica.
                // A rota protegida vai negar o acesso (401) por nao haver Authentication.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
