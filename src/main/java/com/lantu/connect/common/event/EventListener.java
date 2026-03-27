package com.lantu.connect.common.event;

import com.lantu.connect.common.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Consumes platform events for async processing (logging, analytics, etc.).
 */
@Slf4j
@Component
@ConditionalOnBean(RabbitMqConfig.class)
public class EventListener {

    @RabbitListener(queues = RabbitMqConfig.QUEUE_AUDIT_LOG)
    public void handleAuditLogEvent(PlatformEvent event) {
        log.info("[MQ-AuditLog] eventType={}, userId={}, resource={}/{}, displayName={}",
                event.getEventType(), event.getUserId(),
                event.getResourceType(), event.getResourceId(),
                event.getDisplayName());
    }
}
