package com.iceibank.controller;

import com.iceibank.service.BullyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/bully")
public class BullyController {
    private final BullyService bully;
    public BullyController(BullyService bully) { this.bully = bully; }
    public record EleicaoRequest(int origemId, int[] vetorEnvio) {}
    public record CoordenadorRequest(int liderId, int[] vetorEnvio) {}
    @PostMapping("/eleicao") public ResponseEntity<Void> eleicao(@RequestBody EleicaoRequest req) { bully.receberEleicao(req.origemId(), req.vetorEnvio()); return ResponseEntity.ok().build(); }
    @PostMapping("/coordenador") public ResponseEntity<Void> coordenador(@RequestBody CoordenadorRequest req) { bully.receberCoordenador(req.liderId(), req.vetorEnvio()); return ResponseEntity.ok().build(); }
    @GetMapping("/lider") public Map<String,Object> lider() { return bully.status(); }
    @PostMapping("/eleicao/iniciar") public ResponseEntity<Void> iniciar() { bully.iniciarEleicao(); return ResponseEntity.accepted().build(); }
}
