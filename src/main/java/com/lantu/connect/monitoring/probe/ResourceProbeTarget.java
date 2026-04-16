package com.lantu.connect.monitoring.probe;

import lombok.Builder;

import java.util.Map;

@Builder
public record ResourceProbeTarget(
        Long resourceId,
        String resourceType,
        String resourceCode,
        String displayName,
        String registrationProtocol,
        String upstreamEndpoint,
        String upstreamAgentId,
        String credentialRef,
        String transformProfile,
        String modelAlias,
        String executionMode,
        String contextPrompt,
        Map<String, Object> manifest,
        Map<String, Object> specExtra,
        Map<String, Object> parametersSchema,
        String endpoint,
        String protocol,
        String authType,
        Map<String, Object> authConfig,
        Integer timeoutSec,
        Map<String, Object> probeConfig,
        Map<String, Object> canaryPayload
) {
}
