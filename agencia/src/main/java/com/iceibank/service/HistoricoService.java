package com.iceibank.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FUNCIONALIDADE ADICIONAL (secao 2.1 do roteiro): historico de transacoes por conta.
 *
 * Mantem, por conta, a lista dos ultimos eventos que afetaram aquela conta
 * especificamente (criacao, deposito, saque, transferencia enviada/recebida).
 * Exposto via GET /contas/{id}/historico (ver ContasHistoricoController).
 *
 * Isso e diferente do log geral da agencia (data/eventos-agencia-N.jsonl): aqui o
 * historico ja vem filtrado e ordenado para UMA conta especifica, pronto para ser
 * consumido pelo frontend (ex.: extrato).
 */
@Service
public class HistoricoService {

    public record ItemHistorico(String tipo, int[] timestampVetorial, String horaParede, Map<String, Object> detalhes) {}

    private final Map<Integer, List<ItemHistorico>> historicoPorConta = new ConcurrentHashMap<>();

    public void registrar(int idConta, String tipo, int[] timestampVetorial, Map<String, Object> detalhes) {
        historicoPorConta
                .computeIfAbsent(idConta, k -> new CopyOnWriteArrayList<>())
                .add(new ItemHistorico(tipo, timestampVetorial.clone(), Instant.now().toString(), detalhes));
    }

    public List<ItemHistorico> consultar(int idConta) {
        return Collections.unmodifiableList(
                historicoPorConta.getOrDefault(idConta, Collections.emptyList()));
    }
}
