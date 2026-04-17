package com.lantu.connect.monitoring.trace;

import com.lantu.connect.monitoring.entity.TraceSpan;
import com.lantu.connect.monitoring.mapper.TraceSpanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TraceRecorderTest {

    @Mock
    private TraceSpanMapper traceSpanMapper;

    @InjectMocks
    private TraceRecorder traceRecorder;

    @Test
    void nestedSpansReuseTraceIdAndRecordParentChildRelationship() {
        String traceId = traceRecorder.normalizeTraceId("trace-root-1");

        TraceRecorder.TraceSpanScope root = traceRecorder.openSpan(
                traceId,
                "gateway.invoke",
                "unified-gateway",
                Map.of("requestId", "req-1", "resourceType", "agent", "resourceId", "42"));
        try (root) {
            root.tag("resourceCode", "agent.alpha");
            root.log("gateway invoke started");

            TraceRecorder.TraceSpanScope child = traceRecorder.openSpan(
                    null,
                    "protocol.invoke",
                    "protocol-registry",
                    Map.of("protocol", "mcp", "spanKind", "internal"));
            try (child) {
                child.tag("statusCode", 200);
                child.success();
            }

            root.tag("statusCode", 200);
            root.success();
        }

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(traceSpanMapper, times(2)).insert(captor.capture());
        List<TraceSpan> spans = captor.getAllValues();

        TraceSpan childSpan = spans.stream()
                .filter(item -> "protocol.invoke".equals(item.getOperationName()))
                .findFirst()
                .orElseThrow();
        TraceSpan rootSpan = spans.stream()
                .filter(item -> "gateway.invoke".equals(item.getOperationName()))
                .findFirst()
                .orElseThrow();

        assertEquals(traceId, rootSpan.getTraceId());
        assertEquals(traceId, childSpan.getTraceId());
        assertNull(rootSpan.getParentId());
        assertEquals(rootSpan.getId(), childSpan.getParentId());
        assertEquals("success", rootSpan.getStatus());
        assertEquals("success", childSpan.getStatus());
        assertEquals("req-1", String.valueOf(rootSpan.getTags().get("requestId")));
        assertEquals("mcp", String.valueOf(childSpan.getTags().get("protocol")));
        assertNotNull(rootSpan.getLogs());
    }

    @Test
    void failedSpanCapturesErrorMessageInTagsAndLogs() {
        RuntimeException boom = new RuntimeException("protocol timeout");

        TraceRecorder.TraceSpanScope span = traceRecorder.openSpan(
                "trace-root-2",
                "mcp.tools/list",
                "binding-expansion",
                Map.of("resourceType", "mcp", "resourceId", "99"));
        try (span) {
            span.fail(boom);
        }

        ArgumentCaptor<TraceSpan> captor = ArgumentCaptor.forClass(TraceSpan.class);
        verify(traceSpanMapper).insert(captor.capture());
        TraceSpan recorded = captor.getValue();

        assertEquals("error", recorded.getStatus());
        assertEquals("protocol timeout", String.valueOf(recorded.getTags().get("errorMessage")));
        assertTrue(String.valueOf(recorded.getLogs()).contains("protocol timeout"));
    }
}
