package com.iceibank.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class MensageriaConfig {
    public static final String EXCHANGE = "iceibank.eventos";
    public static final String EXCHANGE_ERROS = "iceibank.eventos.dlx";

    @Bean
    TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(EXCHANGE_ERROS, true, false);
    }

    @Bean
    Queue filaAgencia(AppConfig config) {
        return QueueBuilder.durable("fila-agencia-" + config.getIdAgencia())
                .deadLetterExchange(EXCHANGE_ERROS)
                .deadLetterRoutingKey("agencia." + config.getIdAgencia() + ".falha")
                .build();
    }

    @Bean
    Queue filaDeadLetter(AppConfig config) {
        return QueueBuilder.durable("fila-agencia-" + config.getIdAgencia() + ".dlq").build();
    }

    @Bean
    Binding vinculoAgencia(@Qualifier("filaAgencia") Queue filaAgencia, TopicExchange eventosExchange, AppConfig config) {
        return BindingBuilder.bind(filaAgencia).to(eventosExchange)
                .with("agencia." + config.getIdAgencia() + ".creditar");
    }

    @Bean
    Binding vinculoDeadLetter(@Qualifier("filaDeadLetter") Queue filaDeadLetter, DirectExchange deadLetterExchange, AppConfig config) {
        return BindingBuilder.bind(filaDeadLetter).to(deadLetterExchange)
                .with("agencia." + config.getIdAgencia() + ".falha");
    }

    @Bean
    Jackson2JsonMessageConverter conversorMensagens() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    RetryOperationsInterceptor retryConsumidor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(500, 2.0, 5000)
                .recoverer(new org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            RetryOperationsInterceptor retryConsumidor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAdviceChain(retryConsumidor);
        return factory;
    }
}
