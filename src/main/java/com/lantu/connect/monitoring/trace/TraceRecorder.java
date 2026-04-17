package com.lantu.connect.monitoring.trace;

import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TraceRecorder {

    private final TraceSpanMapper traceSpanMapper;
    private final ThreadLocal<Deque<TraceStackFrame>> spanStack = ThreadLocal.withInitial(ArrayDeque::new);

    public String normalizeTraceId(String preferredTraceId) {
        if (StringUtils.hasText(preferredTraceId)) {
            return preferredTraceId.trim();
        }
        TraceStackFrame active = currentFrame();
        return active != null ? active.traceId() : UUID.randomUUID().toString();
    }

    public TraceSpanScope openSpan(String preferredTraceId,
                                   String operationName,
                                   String serviceName,
                                   Map<String, Object> initialTags) {
        String traceId = normalizeTraceId(preferredTraceId);
        Deque<TraceStackFrame> stack = spanStack.get();
        TraceStackFrame parent = stack.peek();

        TraceSpan span = new TraceSpan();
        span.setId(UUID.randomUUID().toString());
        span.setTraceId(traceId);
        span.setParentId(parent == null ? null : parent.spanId());
        span.setOperationName(StringUtils.hasText(operationName) ? operationName.trim() : "operation");
        span.setServiceName(StringUtils.hasText(serviceName) ? serviceName.trim() : "unknown-service");
        span.setStartTime(LocalDateTime.now());
        span.setTags(new LinkedHashMap<>(sanitizeMap(initialTags)));

        stack.push(new TraceStackFrame(traceId, span.getId()));
        return new TraceSpanScope(span);
    }

    private TraceStackFrame currentFrame() {
        Deque<TraceStackFrame> stack = spanStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    private void complete(TraceSpan span,
                          List<Map<String, Object>> logs,
                          String explicitStatus,
                          boolean failed) {
        Deque<TraceStackFrame> stack = spanStack.get();
        if (!stack.isEmpty() && span.getId().equals(stack.peek().spanId())) {
            stack.pop();
        } else {
            stack.removeIf(frame -> span.getId().equals(frame.spanId()));
        }
        if (stack.isEmpty()) {
            spanStack.remove();
        }

        long elapsedMs = Math.max(0L, Duration.between(span.getStartTime(), LocalDateTime.now()).toMillis());
        span.setDuration((int) elapsedMs);
        span.setStatus(resolveStatus(explicitStatus, failed));
        span.setLogs(logs == null || logs.isEmpty() ? List.of() : List.copyOf(logs));
        traceSpanMapper.insert(span);
    }

    private static String resolveStatus(String explicitStatus, boolean failed) {
        if (StringUtils.hasText(explicitStatus)) {
            return explicitStatus.trim().toLowerCase();
        }
        return failed ? "error" : "success";
    }

    private static Map<String, Object> sanitizeMap(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        raw.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null) {
                return;
            }
            out.put(key.trim(), value);
        });
        return out;
    }

    private record TraceStackFrame(String traceId, String spanId) {
    }

    public final class TraceSpanScope implements AutoCloseable {

        private final TraceSpan span;
        private final List<Map<String, Object>> logs = new ArrayList<>();
        private boolean failed;
        private boolean closed;
        private String explicitStatus;

        private TraceSpanScope(TraceSpan span) {
            this.span = span;
        }

        public TraceSpanScope tag(String key, Object value) {
            if (!StringUtils.hasText(key) || value == null) {
                return this;
            }
            if (span.getTags() == null) {
                span.setTags(new LinkedHashMap<>());
            }
            span.getTags().put(key.trim(), value);
            return this;
        }

        public TraceSpanScope tags(Map<String, Object> values) {
            sanitizeMap(values).forEach(this::tag);
            return this;
        }

        public TraceSpanScope log(String message) {
            return log(message, Map.of());
        }

        public TraceSpanScope log(String message, Map<String, Object> context) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", LocalDateTime.now().toString());
            entry.put("message", StringUtils.hasText(message) ? message.trim() : "");
            Map<String, Object> cleanContext = sanitizeMap(context);
            if (!cleanContext.isEmpty()) {
                entry.putAll(cleanContext);
            }
            logs.add(entry);
            return this;
        }

        public TraceSpanScope success() {
            this.explicitStatus = "success";
            return this;
        }

        public TraceSpanScope setStatus(String status) {
            if (StringUtils.hasText(status)) {
                this.explicitStatus = status.trim().toLowerCase();
            }
            return this;
        }

        public TraceSpanScope fail(Throwable throwable) {
            this.failed = true;
            this.explicitStatus = "error";
            if (throwable != null) {
                String message = StringUtils.hasText(throwable.getMessage())
                        ? throwable.getMessage().trim()
                        : throwable.getClass().getSimpleName();
                tag("errorMessage", message);
                log(message, Map.of("errorType", throwable.getClass().getSimpleName()));
            }
            return this;
        }

        public TraceSpanScope fail(String message) {
            this.failed = true;
            this.explicitStatus = "error";
            if (StringUtils.hasText(message)) {
                tag("errorMessage", message.trim());
                log(message.trim());
            }
            return this;
        }

        public String spanId() {
            return span.getId();
        }

        public String traceId() {
            return span.getTraceId();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            complete(span, logs, explicitStatus, failed);
        }
    }
}
