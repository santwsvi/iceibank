package com.iceibank.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Equivalente Java de agencia/src/config.js do roteiro original (Node.js).
 *
 * Guarda:
 *  - o OFFSET pessoal (dois ultimos digitos da matricula/RA), usado apenas em maquina compartilhada
 *  - a lista das 3 agencias (id + URL base)
 *  - a regra de particionamento: idConta % NUMERO_AGENCIAS
 */
@Component
public class AppConfig {

    // TODO: substitua pelo seu OFFSET pessoal (dois ultimos digitos da matricula/RA),
    // necessario apenas se for rodar em uma maquina compartilhada do laboratorio.
    public static final int OFFSET = 0;

    public static final int NUMERO_AGENCIAS = 3;
    public static final int PORTA_BASE = 4000 + OFFSET;

    public record Agencia(int id, String url) {}

    public static final List<Agencia> AGENCIAS = List.of(
            new Agencia(0, "http://localhost:" + PORTA_BASE),
            new Agencia(1, "http://localhost:" + (PORTA_BASE + 1)),
            new Agencia(2, "http://localhost:" + (PORTA_BASE + 2))
    );

    /** ID desta instancia da agencia (0, 1 ou 2), lido da variavel de ambiente AGENCIA_ID. */
    @Value("${AGENCIA_ID:0}")
    private int idAgencia;

    @PostConstruct
    public void validar() {
        boolean existe = AGENCIAS.stream().anyMatch(a -> a.id() == idAgencia);
        if (!existe) {
            throw new IllegalStateException("Agencia " + idAgencia + " nao configurada em AppConfig.AGENCIAS");
        }
    }

    public int getIdAgencia() {
        return idAgencia;
    }

    /** Dado o id da conta, devolve o id da agencia responsavel por ela (particao, nao replicacao). */
    public static int agenciaResponsavel(int idConta) {
        return idConta % NUMERO_AGENCIAS;
    }

    public static Agencia buscarAgencia(int id) {
        return AGENCIAS.stream()
                .filter(a -> a.id() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Agencia " + id + " nao encontrada"));
    }
}
