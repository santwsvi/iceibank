package com.iceibank.controller;

import com.iceibank.service.TransferenciaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class TransferenciasController {

    private final TransferenciaService transferenciaService;
    private final String internalSecret;

    public TransferenciasController(
            TransferenciaService transferenciaService,
            @Value("${iceibank.internal.secret}") String internalSecret
    ) {
        this.transferenciaService = transferenciaService;
        this.internalSecret = internalSecret;
    }

    public record TransferirRequest(int idOrigem, int idDestino, double valor) {}
    public record CreditarRemotoRequest(double valor, int timestampLamport, int origemAgencia) {}

    @PostMapping("/transferencias")
    public ResponseEntity<?> transferir(@RequestBody TransferirRequest body) {
        try {
            return ResponseEntity.ok(
                    Map.of("mensagem", transferenciaService.transferir(body.idOrigem(), body.idDestino(), body.valor()).mensagem()));
        } catch (TransferenciaService.TransferenciaException e) {
            return ResponseEntity.status(e.statusHttp).body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Chamada interna agencia-a-agencia (ver Parte D). Rota liberada em SecurityConfig
     * (nao exige JWT de usuario), mas protegida por um segredo compartilhado enviado
     * no cabecalho X-Agencia-Secret - ver justificativa em TransferenciaService / RESPOSTAS.md.
     */
    @PostMapping("/contas/{id}/creditar-remoto")
    public ResponseEntity<?> creditarRemoto(
            @PathVariable int id,
            @RequestBody CreditarRemotoRequest body,
            @RequestHeader(value = "X-Agencia-Secret", required = false) String segredoRecebido
    ) {
        if (segredoRecebido == null || !segredoRecebido.equals(internalSecret)) {
            return ResponseEntity.status(401).body(Map.of("erro", "Segredo interno agencia-a-agencia ausente ou invalido."));
        }
        try {
            transferenciaService.creditarRemoto(id, body.valor(), body.timestampLamport(), body.origemAgencia());
            return ResponseEntity.ok(Map.of("mensagem", "Credito remoto aplicado."));
        } catch (TransferenciaService.TransferenciaException e) {
            return ResponseEntity.status(e.statusHttp).body(Map.of("erro", e.getMessage()));
        }
    }
}
