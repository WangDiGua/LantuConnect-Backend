package com.lantu.connect.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMqConfig {

    public static final String EXCHANGE_PLATFORM = "lantu.platform.events";
    public static final String QUEUE_NOTIFICATION = "lantu.notification";
    public static final String QUEUE_AUDIT_LOG = "lantu.audit-log";

    @Bean
    public TopicExchange platformExchange() {
        return new TopicExchange(EXCHANGE_PLATFORM, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(QUEUE_NOTIFICATION, true);
    }

    @Bean
    public Queue auditLogQueue() {
        return new Queue(QUEUE_AUDIT_LOG, true);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange platformExchange) {
        return BindingBuilder.bind(notificationQueue).to(platformExchange).with("resource.#");
    }

    @Bean
    public Binding grantNotificationBinding(Queue notificationQueue, TopicExchange platformExchange) {
        return BindingBuilder.bind(notificationQueue).to(platformExchange).with("grant.#");
    }

    @Bean
    public Binding auditLogBinding(Queue auditLogQueue, TopicExchange platformExchange) {
        return BindingBuilder.bind(auditLogQueue).to(platformExchange).with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
