package com.iceibank.controller;

import com.iceibank.service.DeadLetterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Visibilidade operacional da dead-letter queue da agência (rota protegida por JWT). */
@RestController
public class MensageriaController {
    private final DeadLetterService deadLetterService;

    public MensageriaController(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @GetMapping("/mensageria/dlq/status")
    public Map<String, Object> statusDlq() {
        return deadLetterService.status();
    }
}
