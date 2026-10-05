package com.iceibank.controller;

import com.iceibank.config.AppConfig;
import com.iceibank.service.ContaService;
import com.iceibank.service.HistoricoService;
import com.iceibank.service.RelogioVetorial;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * FUNCIONALIDADE ADICIONAL (secao 2.1 do roteiro).
 *
 * Escolhida: "Historico de transacoes por conta" + bonus "rota de status/health-check".
 * Ver descricao completa e evidencias em RESPOSTAS.md.
 */
@RestController
public class ExtraController {

    private final HistoricoService historicoService;
    private final ContaService contaService;
    private final RelogioVetorial relogio;
    private final AppConfig appConfig;

    public ExtraController(HistoricoService historicoService, ContaService contaService,
                            RelogioVetorial relogio, AppConfig appConfig) {
        this.historicoService = historicoService;
        this.contaService = contaService;
        this.relogio = relogio;
        this.appConfig = appConfig;
    }

    /** Historico de eventos de uma conta especifica (extrato simplificado). */
    @GetMapping("/contas/{id}/historico")
    public Object historico(@PathVariable int id) {
        return historicoService.consultar(id);
    }

    /** Health-check: vetor atual e quantidade de contas sob responsabilidade da agencia. */
    @GetMapping("/status")
    public Object status() {
        return Map.of(
                "agencia", appConfig.getIdAgencia(),
                "relogioVetorialAtual", relogio.estadoAtual(),
                "quantidadeContas", contaService.getContas().size()
        );
    }
}
