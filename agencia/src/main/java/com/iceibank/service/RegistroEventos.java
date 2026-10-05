package com.iceibank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iceibank.config.AppConfig;
import com.iceibank.model.Evento;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

/**
 * Equivalente Java de agencia/src/services/eventLog.js.
 *
 * Cada agencia registra todo evento em data/eventos-agencia-<id>.jsonl (uma linha
 * JSON por evento) - essa e a materia-prima da linha do tempo unificada
 * (ver com.iceibank.MesclarLogs / mesclar-logs).
 */
@Component
public class RegistroEventos {

    private static final Logger log = LoggerFactory.getLogger(RegistroEventos.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final AppConfig appConfig;
    private Path caminhoArquivo;

    public RegistroEventos(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @PostConstruct
    public void iniciar() throws IOException {
        Path pastaDados = Paths.get("data");
        Files.createDirectories(pastaDados);
        this.caminhoArquivo = pastaDados.resolve("eventos-agencia-" + appConfig.getIdAgencia() + ".jsonl");
    }

    public synchronized Evento registrar(String tipo, int[] timestampVetorial, Map<String, Object> detalhes) {
        Evento evento = new Evento(
                "agencia-" + appConfig.getIdAgencia(),
                tipo,
                timestampVetorial.clone(),
                Instant.now().toString(),
                detalhes
        );
        try {
            String linha = mapper.writeValueAsString(evento);
            Files.writeString(caminhoArquivo, linha + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Falha ao gravar evento no log", e);
        }
        log.info("[Vetor {}] {} {}", java.util.Arrays.toString(timestampVetorial), tipo, detalhes);
        return evento;
    }
}
