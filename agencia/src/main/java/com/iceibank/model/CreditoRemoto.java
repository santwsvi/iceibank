package com.iceibank.model;

public record CreditoRemoto(
        String messageId,
        int idConta,
        double valor,
        int[] vetorEnvio,
        int origemAgencia,
        int destinoAgencia
) {}
