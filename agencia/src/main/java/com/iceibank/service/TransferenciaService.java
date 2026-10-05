package com.iceibank.service;

import com.iceibank.config.AppConfig;
import com.iceibank.config.MensageriaConfig;
import com.iceibank.model.Conta;
import com.iceibank.model.CreditoRemoto;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferenciaService {
    private final AppConfig appConfig;
    private final ContaService contaService;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final HistoricoService historico;
    private final RabbitTemplate rabbitTemplate;

    public TransferenciaService(AppConfig appConfig, ContaService contaService, RelogioVetorial relogio,
                               RegistroEventos registro, HistoricoService historico, RabbitTemplate rabbitTemplate) {
        this.appConfig = appConfig;
        this.contaService = contaService;
        this.relogio = relogio;
        this.registro = registro;
        this.historico = historico;
        this.rabbitTemplate = rabbitTemplate;
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
        if (contaOrigem == null) throw new TransferenciaException("Conta de origem não encontrada nesta agência.", 404);
        if (!Double.isFinite(valor) || valor <= 0) throw new TransferenciaException("Valor deve ser positivo.", 400);
        if (contaOrigem.getSaldo() < valor) throw new TransferenciaException("Saldo insuficiente.", 400);

        int agenciaDestino = AppConfig.agenciaResponsavel(idDestino);
        int[] vetorDebito = relogio.eventoLocal();
        contaOrigem.setSaldo(contaOrigem.getSaldo() - valor);
        Map<String, Object> detalhesDebito = new LinkedHashMap<>();
        detalhesDebito.put("idOrigem", idOrigem);
        detalhesDebito.put("idDestino", idDestino);
        detalhesDebito.put("valor", valor);
        registro.registrar("TRANSFERENCIA_DEBITO", vetorDebito, detalhesDebito);
        historico.registrar(idOrigem, "TRANSFERENCIA_DEBITO", vetorDebito, detalhesDebito);

        if (agenciaDestino == appConfig.getIdAgencia()) {
            Conta contaDestino = contas.get(idDestino);
            if (contaDestino == null) {
                contaOrigem.setSaldo(contaOrigem.getSaldo() + valor);
                throw new TransferenciaException("Conta de destino não encontrada.", 404);
            }
            int[] vetorCredito = relogio.eventoLocal();
            contaDestino.setSaldo(contaDestino.getSaldo() + valor);
            Map<String, Object> detalhes = Map.of("idOrigem", idOrigem, "idDestino", idDestino, "valor", valor);
            registro.registrar("TRANSFERENCIA_CREDITO", vetorCredito, detalhes);
            historico.registrar(idDestino, "TRANSFERENCIA_CREDITO", vetorCredito, detalhes);
            return new ResultadoTransferencia("Transferência concluída (mesma agência).");
        }

        String messageId = UUID.randomUUID().toString();
        int[] vetorEnvio = relogio.aoEnviar();
        CreditoRemoto mensagem = new CreditoRemoto(messageId, idDestino, valor, vetorEnvio,
                appConfig.getIdAgencia(), agenciaDestino);
        String routingKey = "agencia." + agenciaDestino + ".creditar";
        try {
            rabbitTemplate.convertAndSend(MensageriaConfig.EXCHANGE, routingKey, mensagem, amqp -> {
                amqp.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                amqp.getMessageProperties().setMessageId(messageId);
                return amqp;
            });
        } catch (RuntimeException e) {
            contaOrigem.setSaldo(contaOrigem.getSaldo() + valor);
            int[] vetorFalha = relogio.eventoLocal();
            registro.registrar("TRANSFERENCIA_PUBLICACAO_FALHOU", vetorFalha,
                    Map.of("messageId", messageId, "idDestino", idDestino, "valor", valor));
            throw new TransferenciaException("Não foi possível publicar a transferência no RabbitMQ.", 503);
        }

        registro.registrar("TRANSFERENCIA_PUBLICADA", vetorEnvio,
                Map.of("messageId", messageId, "idOrigem", idOrigem, "idDestino", idDestino,
                        "valor", valor, "agenciaDestino", agenciaDestino));
        return new ResultadoTransferencia("Transferência publicada; o crédito será processado de forma assíncrona. ID: " + messageId);
    }
}
