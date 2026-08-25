package com.iceibank.controller;

import com.iceibank.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Parte F - Autenticacao (JWT).
 *
 * Endpoint de login: POST /auth/login com {"usuario": "...", "senha": "..."}.
 *
 * Decisao de design (documentada em RESPOSTAS.md): as credenciais sao um par
 * usuario/senha simples (nao o id da conta), porque uma pessoa pode movimentar
 * mais de uma conta e o modelo de autenticacao nao deveria ficar amarrado 1-para-1
 * a uma unica conta bancaria. Para efeito didatico deste sprint, os usuarios sao
 * fixos em memoria (sem cadastro/hash de senha) - documentado como simplificacao
 * intencional, nao como pratica recomendada para producao.
 */
@RestController
public class AuthController {

    private final JwtUtil jwtUtil;

    // Usuarios de demonstracao (usuario -> senha). Em um sistema real isso viria de
    // um banco de dados, com senha *sempre* armazenada com hash (ex.: BCrypt).
    private static final Map<String, String> USUARIOS_DEMO = Map.of(
            "aluno", "senha123",
            "operador", "operador123"
    );

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public record LoginRequest(String usuario, String senha) {}

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest body) {
        if (body == null || body.usuario() == null || body.senha() == null) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Informe usuario e senha."));
        }

        String senhaEsperada = USUARIOS_DEMO.get(body.usuario());
        if (senhaEsperada == null || !senhaEsperada.equals(body.senha())) {
            return ResponseEntity.status(401).body(Map.of("erro", "Usuario ou senha invalidos."));
        }

        String token = jwtUtil.gerarToken(body.usuario());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "usuario", body.usuario()
        ));
    }
}
