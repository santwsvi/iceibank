package com.iceibank.service;

import com.iceibank.config.AppConfig;
import com.iceibank.model.Conta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Equivalente Java de agencia/src/controllers/transferenciasController.js.
 *
 * LIMITACAO CONHECIDA (proposital neste sprint): se a chamada REST para a agencia
 * de destino falhar, o debito ja aplicado na origem NAO e revertido - o dinheiro
 * "desaparece" temporariamente. Resolver isso de forma correta (atomicidade sob
 * falha) e o assunto do Sprint 4, com uma transacao distribuida de verdade
 * (2PC/Saga). Por enquanto, so registramos a inconsistencia no log.
 */
@Service
public class TransferenciaService {

    private final AppConfig appConfig;
    private final ContaService contaService;
    private final RelogioLamport relogio;
    private final RegistroEventos registro;
    private final HistoricoService historico;
    private final RestTemplate restTemplate;
    private final String internalSecret;

    public TransferenciaService(
            AppConfig appConfig,
            ContaService contaService,
            RelogioLamport relogio,
            RegistroEventos registro,
            HistoricoService historico,
            RestTemplate restTemplate,
            @Value("${iceibank.internal.secret}") String internalSecret
    ) {
        this.appConfig = appConfig;
        this.contaService = contaService;
        this.relogio = relogio;
        this.registro = registro;
        this.historico = historico;
        this.restTemplate = restTemplate;
        this.internalSecret = internalSecret;
    }

    public static class TransferenciaException extends RuntimeException {
        public final int statusHttp;
        public TransferenciaException(String mensagem, int statusHttp) {
            super(mensagem);
            this.statusHttp = statusHttp;
        }
    }

    public record ResultadoTransferencia(String mensagem) {}

    public ResultadoTransferencia transferir(int idOrigem, int idDestino, double valor) {
        Map<Integer, Conta> contas = contaService.getContas();
        Conta contaOrigem = contas.get(idOrigem);
        if (contaOrigem == null) {
            throw new TransferenciaException("Conta de origem nao encontrada nesta agencia.", 404);
        }
        if (contaOrigem.getSaldo() < valor) {
            throw new TransferenciaException("Saldo insuficiente.", 400);
        }

        int agenciaDestino = AppConfig.agenciaResponsavel(idDestino);

        // O debito e sempre local, pois esta agencia e a dona da conta de origem.
        int tsDebito = relogio.eventoLocal();
        contaOrigem.setSaldo(contaOrigem.getSaldo() - valor);

        Map<String, Object> detalhesDebito = new LinkedHashMap<>();
        detalhesDebito.put("idOrigem", idOrigem);
        detalhesDebito.put("idDestino", idDestino);
        detalhesDebito.put("valor", valor);
        registro.registrar("TRANSFERENCIA_DEBITO", tsDebito, detalhesDebito);
        historico.registrar(idOrigem, "TRANSFERENCIA_DEBITO", tsDebito, detalhesDebito);

        if (agenciaDestino == appConfig.getIdAgencia()) {
            // Caso simples: mesma agencia, credita direto (nao precisa de aoEnviar/aoReceber,
            // pois nao ha mensagem trafegando entre processos distintos).
            Conta contaDestino = contas.get(idDestino);
            if (contaDestino == null) {
                contaOrigem.setSaldo(contaOrigem.getSaldo() + valor); // desfaz o debito
                throw new TransferenciaException("Conta de destino nao encontrada.", 404);
            }
            int tsCredito = relogio.eventoLocal();
            contaDestino.setSaldo(contaDestino.getSaldo() + valor);

            Map<String, Object> detalhesCredito = new LinkedHashMap<>();
            detalhesCredito.put("idOrigem", idOrigem);
            detalhesCredito.put("idDestino", idDestino);
            detalhesCredito.put("valor", valor);
            registro.registrar("TRANSFERENCIA_CREDITO", tsCredito, detalhesCredito);
            historico.registrar(idDestino, "TRANSFERENCIA_CREDITO", tsCredito, detalhesCredito);

            return new ResultadoTransferencia("Transferencia concluida (mesma agencia).");
        }

        // Caso entre agencias: chama a agencia de destino diretamente via REST.
        int tsEnvio = relogio.aoEnviar();
        AppConfig.Agencia destino = AppConfig.buscarAgencia(agenciaDestino);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Agencia-Secret", internalSecret);
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("valor", valor);
            corpo.put("timestampLamport", tsEnvio);
            corpo.put("origemAgencia", appConfig.getIdAgencia());

            restTemplate.postForEntity(
                    destino.url() + "/contas/" + idDestino + "/creditar-remoto",
                    new HttpEntity<>(corpo, headers),
                    Map.class
            );
            return new ResultadoTransferencia("Transferencia concluida (entre agencias).");
        } catch (RestClientException e) {
            // LIMITACAO CONHECIDA: o debito acima NAO e revertido. Ver Sprint 4.
            Map<String, Object> detalhesFalha = new LinkedHashMap<>();
            detalhesFalha.put("idOrigem", idOrigem);
            detalhesFalha.put("idDestino", idDestino);
            detalhesFalha.put("valor", valor);
            detalhesFalha.put("erro", e.getMessage());
            int tsFalha = relogio.eventoLocal();
            registro.registrar("TRANSFERENCIA_FALHOU", tsFalha, detalhesFalha);
            historico.registrar(idOrigem, "TRANSFERENCIA_FALHOU", tsFalha, detalhesFalha);

            throw new TransferenciaException(
                    "Falha ao contatar agencia de destino. Debito ja aplicado - inconsistencia conhecida (ver Sprint 4).",
                    502
            );
        }
    }

    /** Chamada recebida de outra agencia (ver TransferenciasController.creditarRemoto). */
    public void creditarRemoto(int idConta, double valor, int timestampLamportRecebido, int origemAgencia) {
        Conta conta = contaService.getContas().get(idConta);
        if (conta == null) {
            throw new TransferenciaException("Conta nao encontrada nesta agencia.", 404);
        }

        // Ao RECEBER uma mensagem de outra agencia, o relogio de Lamport e atualizado
        // com base no timestamp recebido - regra 3 do algoritmo.
        int ts = relogio.aoReceber(timestampLamportRecebido);

        conta.setSaldo(conta.getSaldo() + valor);

        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("idConta", idConta);
        detalhes.put("valor", valor);
        detalhes.put("origemAgencia", origemAgencia);
        registro.registrar("TRANSFERENCIA_CREDITO_REMOTO", ts, detalhes);
        historico.registrar(idConta, "TRANSFERENCIA_CREDITO_REMOTO", ts, detalhes);
    }
}
