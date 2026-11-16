package com.iceibank.service;

import com.iceibank.config.AppConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BullyService {
    private final int id;
    private final RelogioVetorial clock;
    private final RegistroEventos log;
    private final RestTemplate http;
    private final AtomicInteger leader = new AtomicInteger(-1);
    private final AtomicBoolean election = new AtomicBoolean(false);
    @Value("${iceibank.bully.timeout-ms:1000}") private int timeoutMs;

    public BullyService(AppConfig config, RelogioVetorial clock, RegistroEventos log) {
        this.id = config.getIdAgencia(); this.clock = clock; this.log = log;
        var factory = new SimpleClientHttpRequestFactory();
        this.http = new RestTemplate(factory);
    }

    @PostConstruct public void start() { configurarTimeout(); iniciarEleicao(); }

    private void configurarTimeout() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs); factory.setReadTimeout(timeoutMs);
        http.setRequestFactory(factory);
    }

    public void iniciarEleicao() {
        if (!election.compareAndSet(false, true)) return;
        try {
            int[] ts = clock.eventoLocal(); log.registrar("ELEICAO_INICIADA", ts, Map.of("idAgencia", id));
            boolean responde = false;
            for (var a : AppConfig.AGENCIAS) if (a.id() > id) {
                try { http.postForEntity(a.url()+"/bully/eleicao", Map.of("origemId", id, "vetorEnvio", clock.aoEnviar()), Void.class); responde = true; }
                catch (Exception ignored) { }
            }
            if (!responde) tornarLider();
        } finally { election.set(false); }
    }

    public void receberEleicao(int origemId, int[] vetor) {
        int[] ts = clock.aoReceber(vetor); log.registrar("ELEICAO_RECEBIDA", ts, Map.of("de", origemId, "minhaId", id));
        if (origemId < id) iniciarEleicao();
    }

    private void tornarLider() {
        leader.set(id); log.registrar("TORNOU_LIDER", clock.eventoLocal(), Map.of("idAgencia", id));
        for (var a : AppConfig.AGENCIAS) if (a.id() != id) try {
            http.postForEntity(a.url()+"/bully/coordenador", Map.of("liderId", id, "vetorEnvio", clock.aoEnviar()), Void.class);
        } catch (Exception ignored) { }
    }

    public void receberCoordenador(int liderId, int[] vetor) {
        int[] ts = clock.aoReceber(vetor); leader.set(liderId);
        log.registrar("LIDER_ANUNCIADO", ts, Map.of("liderId", liderId, "agencia", id));
    }

    @Scheduled(fixedDelayString="${iceibank.bully.heartbeat-ms:3000}")
    public void heartbeat() {
        int known = leader.get();
        if (known < 0) { iniciarEleicao(); return; }
        if (known == id) return;
        var agencia = AppConfig.buscarAgencia(known);
        try { http.getForEntity(agencia.url()+"/bully/lider", Map.class); }
        catch (Exception ex) {
            log.registrar("LIDER_NAO_RESPONDEU", clock.eventoLocal(), Map.of("liderAtual", known, "agencia", id));
            leader.set(-1); iniciarEleicao();
        }
    }

    public Map<String,Object> status() { var result = new java.util.LinkedHashMap<String,Object>(); result.put("idAgencia", id); result.put("liderAtual", leader.get() < 0 ? null : leader.get()); return result; }
}
