package com.lantu.connect.monitoring.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.protocol.ProtocolInvokeContext;
import com.lantu.connect.gateway.protocol.ProtocolInvokeResult;
import com.lantu.connect.gateway.protocol.ProtocolInvokerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class SkillProbeHandler implements ResourceProbeHandler {

    private final JdbcTemplate jdbcTemplate;
    private final ProtocolInvokerRegistry protocolInvokerRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String resourceType) {
        return "skill".equalsIgnoreCase(resourceType);
    }

    @Override
    public ResourceProbeResult probe(ResourceProbeTarget target) {
        if (target == null || !StringUtils.hasText(target.contextPrompt())) {
            return new ResourceProbeResult(
                    "down",
                    "skill_canary",
                    "skill context prompt missing",
                    "skill context prompt missing",
                    0L,
                    null,
                    Map.of());
        }
        Map<String, Object> manifest = target.manifest() == null ? Map.of() : target.manifest();
        Map<String, Object> specExtra = target.specExtra() == null ? Map.of() : target.specExtra();
        String runtimeMode = firstText(mapText(specExtra.get("runtimeMode")), mapText(manifest.get("runtimeMode")));
        Map<String, Object> delegate = !nestedMap(specExtra, "delegate").isEmpty()
                ? nestedMap(specExtra, "delegate")
                : nestedMap(manifest, "delegate");
        if ("delegate_agent".equalsIgnoreCase(runtimeMode) || !delegate.isEmpty()) {
            return probeDelegateSkill(target, delegate);
        }
        return probePromptBundleSkill(target);
    }

    private ResourceProbeResult probeDelegateSkill(ResourceProbeTarget target, Map<String, Object> delegate) {
        String resourceId = firstText(mapText(delegate.get("resourceId")), null);
        if (!StringUtils.hasText(resourceId)) {
            return new ResourceProbeResult(
                    "degraded",
                    "skill_canary",
                    "skill delegate resource is missing",
                    "skill delegate resource is missing",
                    0L,
                    null,
                    Map.of());
        }
        Long delegateId = Long.valueOf(resourceId);
        Map<String, Object> base = queryOne("""
                SELECT id, resource_type, resource_code, display_name, status
                FROM t_resource
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """, delegateId);
        Map<String, Object> ext = queryOne("""
                SELECT enabled, registration_protocol, upstream_endpoint, upstream_agent_id,
                       credential_ref, transform_profile, model_alias
                FROM t_resource_agent_ext
                WHERE resource_id = ?
                LIMIT 1
                """, delegateId);
        String endpoint = mapText(ext.get("upstream_endpoint"));
        if (!StringUtils.hasText(endpoint)) {
            return new ResourceProbeResult(
                    "down",
                    "skill_canary",
                    "delegate agent endpoint missing",
                    "delegate agent endpoint missing",
                    0L,
                    null,
                    Map.of("delegateResourceId", delegateId));
        }
        String protocol = firstText(mapText(ext.get("registration_protocol")), "openai_compatible");
        Map<String, Object> payload = new LinkedHashMap<>(target.canaryPayload() == null ? Map.of() : target.canaryPayload());
        Map<String, Object> lantu = new LinkedHashMap<>();
        lantu.put("healthProbe", true);
        lantu.put("skillContext", Map.of(
                "resourceId", target.resourceId(),
                "resourceCode", target.resourceCode(),
                "displayName", target.displayName(),
                "contextPrompt", target.contextPrompt()));
        payload.put("_lantu", lantu);
        Map<String, Object> invokeSpec = new LinkedHashMap<>();
        invokeSpec.put("registrationProtocol", protocol);
        invokeSpec.put("upstreamAgentId", ext.get("upstream_agent_id"));
        invokeSpec.put("credentialRef", ext.get("credential_ref"));
        invokeSpec.put("transformProfile", ext.get("transform_profile"));
        invokeSpec.put("modelAlias", ext.get("model_alias"));
        try {
            ProtocolInvokeResult result = protocolInvokerRegistry.invoke(
                    protocol,
                    endpoint,
                    normalizedTimeout(target.timeoutSec(), 20),
                    "health-" + UUID.randomUUID(),
                    payload,
                    invokeSpec,
                    ProtocolInvokeContext.of(null, delegateId, null));
            if (result.statusCode() >= 200 && result.statusCode() < 300) {
                return new ResourceProbeResult(
                        "healthy",
                        "skill_canary",
                        "skill delegate canary succeeded",
                        null,
                        result.latencyMs(),
                        abbreviate(result.body()),
                        Map.of(
                                "delegateResourceId", delegateId,
                                "delegateResourceCode", mapText(base.get("resource_code")),
                                "latencyMs", result.latencyMs()));
            }
            String failureReason = result.statusCode() == 429
                    ? "skill delegate agent returned 429"
                    : "skill delegate agent returned HTTP " + result.statusCode();
            return new ResourceProbeResult(
                    result.statusCode() == 429 ? "degraded" : "down",
                    "skill_canary",
                    failureReason,
                    failureReason,
                    result.latencyMs(),
                    abbreviate(result.body()),
                    Map.of("delegateResourceId", delegateId));
        } catch (Exception ex) {
            return new ResourceProbeResult(
                    "down",
                    "skill_canary",
                    "skill delegate canary failed",
                    safeMessage(ex),
                    0L,
                    null,
                    Map.of("delegateResourceId", delegateId, "exception", safeMessage(ex)));
        }
    }

    private ResourceProbeResult probePromptBundleSkill(ResourceProbeTarget target) {
        if (!satisfiesRequiredSchema(target.parametersSchema(), target.canaryPayload())) {
            return new ResourceProbeResult(
                    "degraded",
                    "skill_canary",
                    "skill canary payload does not satisfy parameters schema",
                    "skill canary payload does not satisfy parameters schema",
                    0L,
                    null,
                    Map.of("parametersSchema", target.parametersSchema()));
        }
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("contextPrompt", target.contextPrompt());
        bundle.put("parametersSchema", target.parametersSchema());
        bundle.put("input", target.canaryPayload() == null ? Map.of() : target.canaryPayload());
        return new ResourceProbeResult(
                "healthy",
                "skill_canary",
                "skill prompt bundle canary succeeded",
                null,
                0L,
                abbreviate(writeJson(bundle)),
                Map.of("bundlePreview", bundle));
    }

    private Map<String, Object> queryOne(String sql, Long resourceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, resourceId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private boolean satisfiesRequiredSchema(Map<String, Object> schema, Map<String, Object> payload) {
        if (schema == null || schema.isEmpty()) {
            return true;
        }
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        Object requiredRaw = schema.get("required");
        if (requiredRaw instanceof List<?> requiredList) {
            for (Object item : requiredList) {
                String key = item == null ? "" : String.valueOf(item).trim();
                if (!StringUtils.hasText(key) || !safePayload.containsKey(key) || safePayload.get(key) == null) {
                    return false;
                }
            }
        }
        Object propertiesRaw = schema.get("properties");
        if (propertiesRaw instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                Object value = safePayload.get(key);
                if (!(entry.getValue() instanceof Map<?, ?> definition) || value == null) {
                    continue;
                }
                String type = firstText(mapText(definition.get("type")), null);
                if (!StringUtils.hasText(type)) {
                    continue;
                }
                if ("string".equalsIgnoreCase(type) && !(value instanceof String)) {
                    return false;
                }
                if ("number".equalsIgnoreCase(type) && !(value instanceof Number)) {
                    return false;
                }
                if ("boolean".equalsIgnoreCase(type) && !(value instanceof Boolean)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private static int normalizedTimeout(Integer timeoutSec, int fallback) {
        return timeoutSec == null ? fallback : Math.max(1, Math.min(120, timeoutSec));
    }

    private static Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        Object raw = source == null ? null : source.get(key);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return Map.of();
    }

    private static String mapText(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return StringUtils.hasText(value) ? value : null;
    }

    private static String firstText(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    private static String abbreviate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.length() > 1024 ? raw.substring(0, 1021) + "..." : raw;
    }

    private static String safeMessage(Throwable ex) {
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            return ex == null ? "unknown" : ex.getClass().getSimpleName();
        }
        return ex.getMessage();
    }
}
