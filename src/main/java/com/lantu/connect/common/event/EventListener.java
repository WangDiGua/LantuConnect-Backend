package com.lantu.connect.common.event;

import com.lantu.connect.common.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes platform events for async processing (logging, analytics, etc.).
 */
@Slf4j
@Component
@ConditionalOnBean(RabbitMqConfig.class)
public class EventListener {

    @RabbitListener(queues = RabbitMqConfig.QUEUE_AUDIT_LOG)
    public void handleAuditLogEvent(PlatformEvent event) {
        String prevTrace = MDC.get("traceId");
        String prevTask = MDC.get("task");
        MDC.put("traceId", "mq-" + UUID.randomUUID().toString().replace("-", ""));
        MDC.put("task", "EventListener#handleAuditLogEvent");
        try {
            log.info("[MQ-AuditLog] eventType={}, userId={}, resource={}/{}, displayName={}",
                    event.getEventType(), event.getUserId(),
                    event.getResourceType(), event.getResourceId(),
                    event.getDisplayName());
        } finally {
            if (prevTrace != null) {
                MDC.put("traceId", prevTrace);
            } else {
                MDC.remove("traceId");
            }
            if (prevTask != null) {
                MDC.put("task", prevTask);
            } else {
                MDC.remove("task");
            }
        }
    }
}
