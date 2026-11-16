package com.iceibank.controller;

import com.iceibank.model.Conta;
import com.iceibank.service.ContaService;
import com.iceibank.service.RelogioVetorial;
import com.iceibank.service.RegistroEventos;
import com.iceibank.service.CompararVetores;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ContasController {

    private final ContaService contaService;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;

    public ContasController(ContaService contaService, RelogioVetorial relogio, RegistroEventos registro) {
        this.contaService = contaService; this.relogio = relogio; this.registro = registro;
    }

    public record CriarContaRequest(int id, String nomeAluno, Double saldoInicial) {}
    public record ValorRequest(double valor) {}
    public record SincronizarRequest(String tipo, double valor, int[] vetorConhecido) {}

    @PostMapping("/contas")
    public ResponseEntity<?> criarConta(@RequestBody CriarContaRequest body) {
        try {
            double saldoInicial = body.saldoInicial() != null ? body.saldoInicial() : 0.0;
            Conta conta = contaService.criarConta(body.id(), body.nomeAluno(), saldoInicial);
            return ResponseEntity.status(201).body(conta);
        } catch (ContaService.RegraNegocioException e) {
            return ResponseEntity.status(e.statusHttp).body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/contas/{id}")
    public ResponseEntity<?> consultarSaldo(@PathVariable int id) {
        return contaService.consultarSaldo(id)
                .<ResponseEntity<?>>map(conta -> ResponseEntity.ok(Map.of("id", conta.getId(), "nomeAluno", conta.getNomeAluno(), "saldo", conta.getSaldo(), "vetorAtual", relogio.obterVetorAtual())))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("erro", "Conta nao encontrada nesta agencia.")));
    }

    @PostMapping("/contas/{id}/sincronizar")
    public ResponseEntity<?> sincronizar(@PathVariable int id, @RequestBody SincronizarRequest body) {
        var conta = contaService.consultarSaldo(id).orElse(null);
        if (conta == null) return ResponseEntity.status(404).body(Map.of("erro", "Conta nao encontrada nesta agencia."));
        int[] atual = relogio.obterVetorAtual();
        try {
            if (!"IGUAIS".equals(CompararVetores.comparar(body.vetorConhecido(), atual)))
                return ResponseEntity.status(409).body(Map.of("erro", "Conflito detectado: o estado mudou desde a leitura offline.", "vetorConhecido", body.vetorConhecido(), "vetorAtual", atual, "contaAtual", conta));
        } catch (IllegalArgumentException ex) { return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage())); }
        try {
            Conta atualizado = "DEPOSITO".equals(body.tipo()) ? contaService.depositar(id, body.valor()) : "SAQUE".equals(body.tipo()) ? contaService.sacar(id, body.valor()) : null;
            if (atualizado == null) return ResponseEntity.badRequest().body(Map.of("erro", "Tipo de operacao pendente nao suportado."));
            int[] vetor = relogio.obterVetorAtual(); registro.registrar("SINCRONIZACAO_OFFLINE", vetor, Map.of("id", id, "tipo", body.tipo(), "valor", body.valor()));
            return ResponseEntity.ok(Map.of("id", atualizado.getId(), "nomeAluno", atualizado.getNomeAluno(), "saldo", atualizado.getSaldo(), "vetorAtual", vetor));
        } catch (ContaService.RegraNegocioException e) { return ResponseEntity.status(e.statusHttp).body(Map.of("erro", e.getMessage())); }
    }

    @PostMapping("/contas/{id}/depositar")
    public ResponseEntity<?> depositar(@PathVariable int id, @RequestBody ValorRequest body) {
        try {
            return ResponseEntity.ok(contaService.depositar(id, body.valor()));
        } catch (ContaService.RegraNegocioException e) {
            return ResponseEntity.status(e.statusHttp).body(Map.of("erro", e.getMessage()));
        }
    }

    @PostMapping("/contas/{id}/sacar")
    public ResponseEntity<?> sacar(@PathVariable int id, @RequestBody ValorRequest body) {
        try {
            return ResponseEntity.ok(contaService.sacar(id, body.valor()));
        } catch (ContaService.RegraNegocioException e) {
            return ResponseEntity.status(e.statusHttp).body(Map.of("erro", e.getMessage()));
        }
    }
}
