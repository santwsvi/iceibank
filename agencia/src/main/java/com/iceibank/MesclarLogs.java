package com.iceibank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Equivalente Java de agencia/mesclar-logs.js (Parte E).
 *
 * Le todos os arquivos data/eventos-agencia-*.jsonl e monta uma unica linha do
 * tempo, ordenada por relogio de Lamport - para observar concorrencia real entre
 * as 3 agencias.
 *
 * Como executar (depois de já ter gerado eventos rodando as 3 agencias):
 *   mvn compile exec:java -Dexec.mainClass=com.iceibank.MesclarLogs
 * ou, apos empacotar (mvn package), com o classpath do jar:
 *   java -cp target/agencia.jar com.iceibank.MesclarLogs
 */
public class MesclarLogs {

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Path pastaDados = Paths.get("data");

        List<JsonNode> todosEventos = new ArrayList<>();

        if (!Files.isDirectory(pastaDados)) {
            System.out.println("Pasta 'data' nao encontrada. Rode as agencias e gere alguns eventos primeiro.");
            return;
        }

        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pastaDados, "eventos-*.jsonl")) {
            for (Path arquivo : arquivos) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (linha.isBlank()) continue;
                    todosEventos.add(mapper.readTree(linha));
                }
            }
        }

        todosEventos.sort(Comparator.comparingInt(e -> e.get("timestampLamport").asInt()));

        System.out.println("=== Linha do tempo unificada (ordenada por relogio de Lamport) ===");
        for (JsonNode evento : todosEventos) {
            System.out.printf(
                    "[Lamport %d] (%s) %s - %s %s%n",
                    evento.get("timestampLamport").asInt(),
                    evento.get("horaParede").asText(),
                    evento.get("agencia").asText(),
                    evento.get("tipo").asText(),
                    evento.get("detalhes").toString()
            );
        }
    }
}
