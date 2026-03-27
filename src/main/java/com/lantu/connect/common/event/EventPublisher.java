package com.lantu.connect.common.event;

import com.lantu.connect.common.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Publishes platform events to RabbitMQ when available;
 * silently no-ops when RabbitMQ is not configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RabbitMqConfig.class)
public class EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(PlatformEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_PLATFORM,
                    event.getEventType(),
                    event);
            log.debug("Published event: {} for resource {}/{}", event.getEventType(), event.getResourceType(), event.getResourceId());
        } catch (Exception e) {
            log.warn("Failed to publish event {}: {}", event.getEventType(), e.getMessage());
        }
    }
}
