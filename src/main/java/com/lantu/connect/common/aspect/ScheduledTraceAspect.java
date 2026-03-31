package com.lantu.connect.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 为 {@link org.springframework.scheduling.annotation.Scheduled} 任务补齐 MDC traceId/task，
 * 避免异步任务日志无法与 HTTP 链路关联或完全无 traceId。
 */
@Aspect
@Component
@Order(0)
public class ScheduledTraceAspect {

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduled(ProceedingJoinPoint pjp) throws Throwable {
        String prevTrace = MDC.get("traceId");
        String prevTask = MDC.get("task");
        String trace = "sched-" + UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", trace);
        MDC.put("task", pjp.getSignature().getDeclaringTypeName() + "#" + pjp.getSignature().getName());
        try {
            return pjp.proceed();
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
