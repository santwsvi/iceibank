package com.iceibank.model;

import java.util.Map;

/**
 * Evento registrado no log .jsonl de uma agencia.
 *
 * Guarda dois carimbos de tempo: timestampLamport (o relogio logico) e horaParede
 * (o relogio fisico da maquina, so para fins de comparacao - nao usado para
 * nenhuma decisao do sistema).
 */
public record Evento(
        String agencia,
        String tipo,
        int timestampLamport,
        String horaParede,
        Map<String, Object> detalhes
) {
}
