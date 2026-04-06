package com.lantu.connect.common.aspect;

import com.lantu.connect.common.annotation.AuditLog;
import com.lantu.connect.common.audit.AuditSnapshotEntry;
import com.lantu.connect.common.audit.JaversAuditService;
import com.lantu.connect.common.web.ClientIpResolver;
import com.lantu.connect.sysconfig.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLog 切面
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Slf4j
@Aspect
@Component
@lombok.RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;
    private final JaversAuditService javersAuditService;
    private final ClientIpResolver clientIpResolver;

    @Around("@annotation(com.lantu.connect.common.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature sig = (MethodSignature) point.getSignature();
        AuditLog ann = sig.getMethod().getAnnotation(AuditLog.class);
        String userId = null, ip = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            userId = req.getHeader("X-User-Id");
            ip = clientIpResolver.resolve(req);
        }
        String result = "success";
        Object responseBody = null;
        Throwable throwable = null;
        try {
            responseBody = point.proceed();
            return responseBody;
        } catch (Throwable e) {
            result = "failure";
            throwable = e;
            throw e;
        }
        finally {
            String traceId = null;
            if (attrs != null) {
                traceId = attrs.getRequest().getHeader("X-Request-Id");
            }
            com.lantu.connect.sysconfig.entity.AuditLog row = new com.lantu.connect.sysconfig.entity.AuditLog();
            row.setId(UUID.randomUUID().toString());
            row.setUserId(userId != null ? userId : "0");
            row.setUsername(userId != null ? "user-" + userId : "anonymous");
            row.setAction(ann.action());
            row.setResource(ann.resource());
            row.setResult(result);
            row.setIp(ip != null ? ip : "0.0.0.0");
            row.setCreateTime(LocalDateTime.now());
            row.setDetails("elapsedMs=" + (System.currentTimeMillis() - start));
            try {
                auditLogMapper.insert(row);
            } catch (RuntimeException e) {
                log.warn("写入审计日志失败: {}", e.getMessage());
            }

            AuditSnapshotEntry snapshot = new AuditSnapshotEntry();
            snapshot.setId(row.getId());
            snapshot.setAction(ann.action());
            snapshot.setResource(ann.resource());
            snapshot.setUserId(userId != null ? userId : "0");
            snapshot.setResult(result);
            snapshot.setTraceId(traceId);
            snapshot.setCreateTime(row.getCreateTime());
            snapshot.setRequestArgs(point.getArgs());
            snapshot.setResponseBody(result.equals("success") ? responseBody : (throwable != null ? throwable.getMessage() : null));
            javersAuditService.commit(snapshot.getUserId(), snapshot);

            log.info("[AUDIT] action={}, resource={}, userId={}, ip={}, result={}, elapsed={}ms",
                    ann.action(), ann.resource(), userId, ip, result, System.currentTimeMillis() - start);
        }
    }
}
