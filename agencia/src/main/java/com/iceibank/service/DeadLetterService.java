package com.iceibank.service;

import com.iceibank.config.AppConfig;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DeadLetterService {
    private final RabbitAdmin rabbitAdmin;
    private final String nomeFila;

    public DeadLetterService(RabbitAdmin rabbitAdmin, AppConfig appConfig) {
        this.rabbitAdmin = rabbitAdmin;
        this.nomeFila = "fila-agencia-" + appConfig.getIdAgencia() + ".dlq";
    }

    public Map<String, Object> status() {
        QueueInformation info = rabbitAdmin.getQueueInfo(nomeFila);
        return Map.of("fila", nomeFila, "mensagensPendentes", info == null ? 0 : info.getMessageCount());
    }
}
