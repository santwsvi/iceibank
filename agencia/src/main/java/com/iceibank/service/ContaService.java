package com.iceibank.service;

import com.iceibank.config.AppConfig;
import com.iceibank.model.Conta;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Equivalente Java de agencia/src/controllers/contasController.js (a parte de
 * modelo/regra de negocio - o controller HTTP fica em ContasController).
 *
 * Contas guardadas em memoria (Map) neste sprint - sem banco de dados. Se o
 * processo da agencia for reiniciado, as contas somem. Isso e esperado.
 */
@Service
public class ContaService {

    private final AppConfig appConfig;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final HistoricoService historico;

    private final Map<Integer, Conta> contas = new ConcurrentHashMap<>();

    public ContaService(AppConfig appConfig, RelogioVetorial relogio, RegistroEventos registro, HistoricoService historico) {
        this.appConfig = appConfig;
        this.relogio = relogio;
        this.registro = registro;
        this.historico = historico;
    }

    public static class RegraNegocioException extends RuntimeException {
        public final int statusHttp;
        public RegraNegocioException(String mensagem, int statusHttp) {
            super(mensagem);
            this.statusHttp = statusHttp;
        }
    }

    public Conta criarConta(int id, String nomeAluno, double saldoInicial) {
        if (AppConfig.agenciaResponsavel(id) != appConfig.getIdAgencia()) {
            throw new RegraNegocioException("Conta " + id + " nao pertence a esta agencia.", 400);
        }
        if (contas.containsKey(id)) {
            throw new RegraNegocioException("Conta ja existe.", 409);
        }

        int[] ts = relogio.eventoLocal();
        Conta conta = new Conta(id, nomeAluno, saldoInicial);
        contas.put(id, conta);

        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("id", id);
        detalhes.put("nomeAluno", nomeAluno);
        detalhes.put("saldoInicial", saldoInicial);
        registro.registrar("CRIAR_CONTA", ts, detalhes);
        historico.registrar(id, "CRIAR_CONTA", ts, detalhes);

        return conta;
    }

    public Optional<Conta> consultarSaldo(int id) {
        return Optional.ofNullable(contas.get(id));
    }

    public Conta depositar(int id, double valor) {
        Conta conta = contas.get(id);
        if (conta == null) {
            throw new RegraNegocioException("Conta nao encontrada nesta agencia.", 404);
        }

        int[] ts = relogio.eventoLocal();
        conta.setSaldo(conta.getSaldo() + valor);

        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("id", id);
        detalhes.put("valor", valor);
        detalhes.put("novoSaldo", conta.getSaldo());
        registro.registrar("DEPOSITO", ts, detalhes);
        historico.registrar(id, "DEPOSITO", ts, detalhes);

        return conta;
    }

    public Conta sacar(int id, double valor) {
        Conta conta = contas.get(id);
        if (conta == null) {
            throw new RegraNegocioException("Conta nao encontrada nesta agencia.", 404);
        }
        if (conta.getSaldo() < valor) {
            throw new RegraNegocioException("Saldo insuficiente.", 400);
        }

        int[] ts = relogio.eventoLocal();
        conta.setSaldo(conta.getSaldo() - valor);

        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("id", id);
        detalhes.put("valor", valor);
        detalhes.put("novoSaldo", conta.getSaldo());
        registro.registrar("SAQUE", ts, detalhes);
        historico.registrar(id, "SAQUE", ts, detalhes);

        return conta;
    }

    /** Acesso direto ao mapa de contas - usado pelo TransferenciaService. */
    public Map<Integer, Conta> getContas() {
        return contas;
    }
}
