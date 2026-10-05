package com.iceibank.service;

import com.iceibank.config.AppConfig;
import com.iceibank.config.MensageriaConfig;
import com.iceibank.model.Conta;
import com.iceibank.model.CreditoRemoto;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Consumidor de créditos da fila da agência; falhas são repetidas e depois encaminhadas à DLQ. */
@Component
public class ConsumidorCreditos {
    private final AppConfig appConfig;
    private final ContaService contaService;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final HistoricoService historico;
    private final Set<String> processadas = ConcurrentHashMap.newKeySet();

    public ConsumidorCreditos(AppConfig appConfig, ContaService contaService, RelogioVetorial relogio,
                              RegistroEventos registro, HistoricoService historico) {
        this.appConfig = appConfig;
        this.contaService = contaService;
        this.relogio = relogio;
        this.registro = registro;
        this.historico = historico;
    }

    @RabbitListener(queues = "fila-agencia-${AGENCIA_ID:0}")
    public void consumir(CreditoRemoto mensagem) {
        if (mensagem.destinoAgencia() != appConfig.getIdAgencia()
                || AppConfig.agenciaResponsavel(mensagem.idConta()) != appConfig.getIdAgencia()) {
            throw new IllegalArgumentException("Mensagem endereçada à agência ou partição incorreta.");
        }
        int[] vetor = relogio.aoReceber(mensagem.vetorEnvio());
        if (processadas.contains(mensagem.messageId())) {
            registro.registrar("CREDITO_DUPLICADO_IGNORADO", vetor, Map.of("messageId", mensagem.messageId()));
            return;
        }
        Conta conta = contaService.getContas().get(mensagem.idConta());
        if (conta == null) {
            registro.registrar("CREDITO_REMOTO_FALHOU", vetor,
                    Map.of("messageId", mensagem.messageId(), "idConta", mensagem.idConta(),
                            "valor", mensagem.valor(), "origemAgencia", mensagem.origemAgencia(),
                            "motivo", "conta não encontrada; mensagem encaminhada à DLQ após tentativas"));
            throw new IllegalStateException("Conta destino não encontrada: " + mensagem.idConta());
        }

        conta.setSaldo(conta.getSaldo() + mensagem.valor());
        processadas.add(mensagem.messageId());
        Map<String, Object> detalhes = Map.of("messageId", mensagem.messageId(), "idConta", mensagem.idConta(),
                "valor", mensagem.valor(), "origemAgencia", mensagem.origemAgencia());
        registro.registrar("TRANSFERENCIA_CREDITO_REMOTO", vetor, detalhes);
        historico.registrar(mensagem.idConta(), "TRANSFERENCIA_CREDITO_REMOTO", vetor, detalhes);
    }
}
