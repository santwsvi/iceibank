package com.iceibank.service;

import com.iceibank.config.AppConfig;
import org.springframework.stereotype.Component;

/** Relógio vetorial com uma posição por agência do ICEIBank. */
@Component
public class RelogioVetorial {
    private final int idAgencia;
    private final int[] vetor;

    public RelogioVetorial(AppConfig appConfig) {
        this.idAgencia = appConfig.getIdAgencia();
        this.vetor = new int[AppConfig.NUMERO_AGENCIAS];
    }

    public synchronized int[] eventoLocal() {
        vetor[idAgencia]++;
        return vetor.clone();
    }

    public synchronized int[] aoEnviar() {
        vetor[idAgencia]++;
        return vetor.clone();
    }

    public synchronized int[] aoReceber(int[] vetorRecebido) {
        if (vetorRecebido == null || vetorRecebido.length != vetor.length) {
            throw new IllegalArgumentException("O vetor recebido deve ter uma posição por agência.");
        }
        for (int i = 0; i < vetor.length; i++) {
            if (vetorRecebido[i] < 0) {
                throw new IllegalArgumentException("Contadores vetoriais não podem ser negativos.");
            }
            vetor[i] = Math.max(vetor[i], vetorRecebido[i]);
        }
        vetor[idAgencia]++;
        return vetor.clone();
    }

    public synchronized int[] estadoAtual() {
        return vetor.clone();
    }

    public synchronized int[] obterVetorAtual() {
        return vetor.clone();
    }
}
