package com.iceibank.controller;

import com.iceibank.model.Conta;
import com.iceibank.service.ContaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ContasController {

    private final ContaService contaService;

    public ContasController(ContaService contaService) {
        this.contaService = contaService;
    }

    public record CriarContaRequest(int id, String nomeAluno, Double saldoInicial) {}
    public record ValorRequest(double valor) {}

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
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("erro", "Conta nao encontrada nesta agencia.")));
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
