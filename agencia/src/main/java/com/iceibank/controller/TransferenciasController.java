package com.iceibank.controller;

import com.iceibank.service.TransferenciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class TransferenciasController {

    private final TransferenciaService transferenciaService;
    public TransferenciasController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    public record TransferirRequest(int idOrigem, int idDestino, double valor) {}

    @PostMapping("/transferencias")
    public ResponseEntity<?> transferir(@RequestBody TransferirRequest body) {
        try {
            return ResponseEntity.ok(
                    Map.of("mensagem", transferenciaService.transferir(body.idOrigem(), body.idDestino(), body.valor()).mensagem()));
        } catch (TransferenciaService.TransferenciaException e) {
            return ResponseEntity.status(e.statusHttp).body(Map.of("erro", e.getMessage()));
        }
    }
}
