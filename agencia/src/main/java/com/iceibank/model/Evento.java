package com.iceibank.model;

import java.util.Map;

/** Evento do log com relógio vetorial e horário físico apenas para exibição. */
public record Evento(
        String agencia,
        String tipo,
        int[] timestampVetorial,
        String horaParede,
        Map<String, Object> detalhes
) {}
