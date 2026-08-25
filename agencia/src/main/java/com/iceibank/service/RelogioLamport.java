package com.iceibank.service;

import org.springframework.stereotype.Component;

/**
 * Relogio logico de Lamport (uma instancia por agencia/processo).
 *
 * Tres regras:
 *  1. Antes de qualquer evento local, incrementa o contador.
 *  2. Ao enviar uma mensagem, incrementa o contador e anexa o valor a mensagem.
 *  3. Ao receber uma mensagem com timestamp t, ajusta o contador para max(contador, t) + 1.
 *
 * "synchronized" e necessario aqui: um servidor Spring Boot atende requisicoes em
 * varias threads ao mesmo tempo, e o contador e estado compartilhado entre elas.
 * Sem isso, duas requisicoes simultaneas poderiam ler/incrementar o contador de forma
 * inconsistente (condicao de corrida).
 */
@Component
public class RelogioLamport {

    private int contador = 0;

    public synchronized int eventoLocal() {
        contador += 1;
        return contador;
    }

    public synchronized int aoEnviar() {
        contador += 1;
        return contador;
    }

    public synchronized int aoReceber(int timestampRecebido) {
        contador = Math.max(contador, timestampRecebido) + 1;
        return contador;
    }

    public synchronized int valorAtual() {
        return contador;
    }
}
