package com.unesp.fleetanalyticsapi.infra.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.messaging.queue-position}")
    private String positionQueueName;

    @Value("${app.messaging.queue-telemetry}")
    private String telemetryQueueName;

    @Value("${app.messaging.queue-event}")
    private String eventQueueName;

    @Bean
    DirectExchange telemetryDlExchange() {
        return new DirectExchange("telemetry.dlx", true, false);
    }

    @Bean
    Queue positionQueue() {
        return QueueBuilder.durable(positionQueueName)
                .deadLetterExchange("telemetry.dlx")
                .deadLetterRoutingKey("telemetry.dlq.position")
                .build();
    }

    @Bean
    Queue telemetryQueue() {
        return QueueBuilder.durable(telemetryQueueName)
                .deadLetterExchange("telemetry.dlx")
                .deadLetterRoutingKey("telemetry.dlq.telemetry")
                .build();
    }

    @Bean
    Queue eventQueue() {
        return QueueBuilder.durable(eventQueueName)
                .deadLetterExchange("telemetry.dlx")
                .deadLetterRoutingKey("telemetry.dlq.event")
                .build();
    }

    @Bean
    Queue positionDlq() {
        return QueueBuilder.durable("fleet.telemetry.position.dlq").build();
    }

    @Bean
    Queue telemetryDlq() {
        return QueueBuilder.durable("fleet.telemetry.telemetry.dlq").build();
    }

    @Bean
    Queue eventDlq() {
        return QueueBuilder.durable("fleet.telemetry.event.dlq").build();
    }

    @Bean
    Binding positionDlBinding(
            @Qualifier("positionDlq") Queue dlq,
            DirectExchange dlExchange) {
        return BindingBuilder.bind(dlq).to(dlExchange).with("telemetry.dlq.position");
    }

    @Bean
    Binding telemetryDlBinding(
            @Qualifier("telemetryDlq") Queue dlq,
            DirectExchange dlExchange) {
        return BindingBuilder.bind(dlq).to(dlExchange).with("telemetry.dlq.telemetry");
    }

    @Bean
    Binding eventDlBinding(
            @Qualifier("eventDlq") Queue dlq,
            DirectExchange dlExchange) {
        return BindingBuilder.bind(dlq).to(dlExchange).with("telemetry.dlq.event");
    }

    @Bean
    MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    TopicExchange amqTopic() {
        return new TopicExchange("amq.topic", true, false);
    }

    @Bean
    Binding positionBinding(@Qualifier("positionQueue") Queue queue, TopicExchange amqTopic) {
        return BindingBuilder.bind(queue).to(amqTopic).with("telemetry.position");
    }

    @Bean
    Binding telemetryBinding(@Qualifier("telemetryQueue") Queue queue, TopicExchange amqTopic) {
        return BindingBuilder.bind(queue).to(amqTopic).with("telemetry.telemetry");
    }

    @Bean
    Binding eventBinding(@Qualifier("eventQueue") Queue queue, TopicExchange amqTopic) {
        return BindingBuilder.bind(queue).to(amqTopic).with("telemetry.event");
    }
}
