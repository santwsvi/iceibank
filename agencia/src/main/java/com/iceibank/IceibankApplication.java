package com.iceibank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da agencia do ICEIBank (Sprint 1).
 *
 * Cada agencia roda a partir deste MESMO codigo - o que muda entre elas e a
 * variavel de ambiente AGENCIA_ID (0, 1 ou 2), lida em com.iceibank.config.AppConfig.
 *
 * Exemplo de execucao (3 terminais):
 *   AGENCIA_ID=0 mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=4000
 *   AGENCIA_ID=1 mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=4001
 *   AGENCIA_ID=2 mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=4002
 *
 * Ou, apos "mvn package", rodando o jar diretamente:
 *   set AGENCIA_ID=0 && set PORTA=4000 && java -jar target/agencia.jar   (Windows/PowerShell: $env:AGENCIA_ID=0; $env:PORTA=4000; java -jar target/agencia.jar)
 */
@SpringBootApplication
public class IceibankApplication {

    public static void main(String[] args) {
        SpringApplication.run(IceibankApplication.class, args);
    }
}
