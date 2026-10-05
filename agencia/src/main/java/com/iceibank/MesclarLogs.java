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

/** Combina logs JSONL e compara vetores para detectar concorrência causal. */
public class MesclarLogs {
    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Path pastaDados = Paths.get("data");
        List<JsonNode> eventos = new ArrayList<>();
        if (!Files.isDirectory(pastaDados)) {
            System.out.println("Pasta 'data' não encontrada. Rode as agências e gere eventos primeiro.");
            return;
        }
        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pastaDados, "eventos-*.jsonl")) {
            for (Path arquivo : arquivos) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (!linha.isBlank()) eventos.add(mapper.readTree(linha));
                }
            }
        }
        eventos.sort(Comparator.comparing(e -> e.path("horaParede").asText()));
        System.out.println("=== Linha do tempo (ordenada por hora de parede) ===");
        for (JsonNode e : eventos) {
            System.out.printf("[%s] vetor=%s %s %s%n", e.path("agencia").asText(),
                    e.path("timestampVetorial"), e.path("tipo").asText(), e.path("detalhes"));
        }

        System.out.println("\n=== Pares de eventos CONCORRENTES entre agências diferentes ===");
        boolean encontrou = false;
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = i + 1; j < eventos.size(); j++) {
                JsonNode a = eventos.get(i), b = eventos.get(j);
                if (a.path("agencia").asText().equals(b.path("agencia").asText())) continue;
                if (comparar(a.path("timestampVetorial"), b.path("timestampVetorial")) == Relacao.CONCORRENTES) {
                    encontrou = true;
                    System.out.printf("[%s] %s %s x [%s] %s %s%n", a.path("agencia").asText(),
                            a.path("tipo").asText(), a.path("timestampVetorial"), b.path("agencia").asText(),
                            b.path("tipo").asText(), b.path("timestampVetorial"));
                }
            }
        }
        if (!encontrou) System.out.println("(nenhum par concorrente encontrado nesta execução)");
    }

    enum Relacao { ANTES, DEPOIS, IGUAIS, CONCORRENTES }

    static Relacao comparar(JsonNode a, JsonNode b) {
        if (!a.isArray() || !b.isArray() || a.size() != b.size())
            throw new IllegalArgumentException("Vetores ausentes ou com dimensões diferentes.");
        boolean aMenor = false, bMenor = false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).asInt() < b.get(i).asInt()) aMenor = true;
            if (a.get(i).asInt() > b.get(i).asInt()) bMenor = true;
        }
        if (!aMenor && !bMenor) return Relacao.IGUAIS;
        if (aMenor && !bMenor) return Relacao.ANTES;
        if (bMenor && !aMenor) return Relacao.DEPOIS;
        return Relacao.CONCORRENTES;
    }
}
