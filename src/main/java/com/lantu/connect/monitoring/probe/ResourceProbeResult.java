package com.lantu.connect.monitoring.probe;

import java.util.Map;

public record ResourceProbeResult(
        String healthStatus,
        String probeStrategy,
        String summary,
        String failureReason,
        Long latencyMs,
        String payloadSummary,
        Map<String, Object> evidence
) {
}
