package com.lantu.connect.gateway.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.gateway.capability.dto.CapabilityImportRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityImportSuggestionVO;
import com.lantu.connect.gateway.protocol.AgentPlatformAdapterSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CapabilityImportDetector {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CapabilityImportSuggestionVO detect(CapabilityImportRequest request) {
        String source = request == null || request.getSource() == null ? "" : request.getSource().trim();
        String preferredType = normalizeType(request == null ? null : request.getPreferredType());
        if (StringUtils.hasText(preferredType)) {
            return buildSuggestion(preferredType, "medium", "Type specified by caller", source, request, List.of());
        }
        if (looksLikeJson(source)) {
            CapabilityImportSuggestionVO byJson = detectFromJson(source, request);
            if (byJson != null) {
                return byJson;
            }
        }
        if (looksLikeUrl(source)) {
            return detectFromUrl(source, request);
        }
        return buildSuggestion("skill", "medium", "Detected as prompt-style capability from plain text", source, request, List.of());
    }

    private CapabilityImportSuggestionVO detectFromJson(String source, CapabilityImportRequest request) {
        try {
            JsonNode root = objectMapper.readTree(source);
            if (root.has("mcpServers") || root.has("jsonrpc") || root.has("transport") || root.has("command")) {
                List<String> warnings = root.has("command")
                        ? List.of("Detected command/stdio MCP config. Registration only supports remotely reachable MCP services over http(s)/ws(s).")
                        : List.of();
                return buildSuggestion("mcp", "high", "Detected MCP / JSON-RPC config traits", source, request, warnings);
            }
            if (root.has("anthropic_version") || root.has("messages")) {
                return buildSuggestion("agent", "high", "Detected Anthropic Messages request shape", source, request, List.of());
            }
            if (root.has("contents") || root.has("generationConfig")) {
                return buildSuggestion("agent", "high", "Detected Gemini generateContent request shape", source, request, List.of());
            }
            if (root.has("contextPrompt") || root.has("parametersSchema") || root.has("bindings")) {
                return buildSuggestion("skill", "high", "Detected Skill prompt / schema traits", source, request, List.of());
            }
            if (root.has("openapi") || root.has("paths")) {
                return buildSuggestion(
                        "skill",
                        "low",
                        "Detected OpenAPI document, mapped to composable capability draft by default",
                        source,
                        request,
                        List.of("OpenAPI imports still need human confirmation before publishing."));
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private CapabilityImportSuggestionVO detectFromUrl(String source, CapabilityImportRequest request) {
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        String adapterId = AgentPlatformAdapterSupport.resolveAdapterId(Map.of(), source, null, null, null);
        if (AgentPlatformAdapterSupport.ADAPTER_TENCENT_YUANQI.equals(adapterId)) {
            return buildSuggestion("agent", "high", "Detected Tencent Yuanqi agent endpoint", source, request, List.of());
        }
        if (normalized.startsWith("ws://") || normalized.startsWith("wss://")) {
            return buildSuggestion("mcp", "high", "Detected WebSocket MCP endpoint", source, request, List.of());
        }
        if (normalized.contains("anthropic.com") || normalized.contains("/v1/messages")) {
            return buildSuggestion("agent", "high", "Detected Anthropic upstream endpoint", source, request, List.of());
        }
        if (normalized.contains("generativelanguage.googleapis.com") || normalized.contains(":generatecontent")) {
            return buildSuggestion("agent", "high", "Detected Gemini upstream endpoint", source, request, List.of());
        }
        if (normalized.contains("dashscope") || normalized.contains("bailian")) {
            return buildSuggestion("agent", "high", "Detected Bailian upstream endpoint", source, request, List.of());
        }
        if (normalized.contains("api.deepseek.com")
                || normalized.contains("openrouter.ai")
                || normalized.contains("ollama")
                || normalized.contains("11434")
                || normalized.contains("openai.com")
                || normalized.contains("/v1/responses")
                || normalized.contains("/chat/completions")
                || normalized.contains("/v1/chat-messages")
                || normalized.contains("/v1/workflows/run")
                || normalized.contains("/v2/agent/ai_assistant/run")) {
            return buildSuggestion("agent", "high", "Detected remote agent / provider endpoint", source, request, List.of());
        }
        if (normalized.contains("/mcp") || normalized.contains("sse")) {
            return buildSuggestion("mcp", "medium", "Detected MCP service traits from endpoint", source, request, List.of());
        }
        return buildSuggestion(
                "agent",
                "low",
                "Detected generic HTTP capability endpoint and mapped to remote agent by default",
                source,
                request,
                List.of("If this is actually MCP, OpenAPI, or prompt capability, please switch the type on the confirmation step."));
    }

    private CapabilityImportSuggestionVO buildSuggestion(String type,
                                                         String confidence,
                                                         String reason,
                                                         String source,
                                                         CapabilityImportRequest request,
                                                         List<String> warnings) {
        String displayName = firstText(request == null ? null : request.getDisplayName(), inferDisplayName(type, source));
        String description = firstText(request == null ? null : request.getDescription(), reason);
        Map<String, Object> defaults = buildDefaults(type, source);
        Map<String, Object> capabilities = buildCapabilities(type, source);
        return CapabilityImportSuggestionVO.builder()
                .detectedType(type)
                .confidence(confidence)
                .reason(reason)
                .displayName(displayName)
                .resourceCode(generateResourceCode(displayName, type))
                .description(description)
                .runtimeMode(inferRuntimeMode(type, source))
                .inputSchema(defaultInputSchema(type))
                .defaults(defaults)
                .authRefs(new LinkedHashMap<>())
                .bindings(List.of())
                .capabilities(capabilities)
                .requiresConfirmation(!"high".equalsIgnoreCase(confidence))
                .warnings(warnings)
                .build();
    }

    private Map<String, Object> defaultInputSchema(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        if ("mcp".equals(type)) {
            props.put("method", Map.of("type", "string"));
            props.put("params", Map.of("type", "object"));
        } else {
            props.put("input", Map.of("type", "string"));
        }
        schema.put("properties", props);
        return schema;
    }

    private Map<String, Object> buildDefaults(String type, String source) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        if ("skill".equals(type)) {
            defaults.put("contextPrompt", source);
            return defaults;
        }
        if (looksLikeUrl(source)) {
            defaults.put("endpoint", source.trim());
        }
        if ("agent".equals(type)) {
            String adapterId = AgentPlatformAdapterSupport.resolveAdapterId(Map.of(), source, null, null, null);
            String transformProfile = AgentPlatformAdapterSupport.defaultTransformProfile(adapterId, null);
            if (StringUtils.hasText(transformProfile)) {
                defaults.put("transformProfile", transformProfile);
            }
            defaults.put("modelAlias", inferModelAlias(source));
        }
        return defaults;
    }

    private Map<String, Object> buildCapabilities(String type, String source) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        if (!"agent".equals(type)) {
            return capabilities;
        }
        String normalized = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        String adapterId = AgentPlatformAdapterSupport.resolveAdapterId(Map.of(), source, null, null, null);
        capabilities.put("agentType", "http_api");
        capabilities.put("mode", "SUBAGENT");
        if (StringUtils.hasText(adapterId)) {
            capabilities.put("registrationProtocol", AgentPlatformAdapterSupport.protocolFamily(adapterId, "openai_compatible"));
            capabilities.put("providerPreset", adapterId);
        } else if (normalized.contains("anthropic.com") || normalized.contains("/v1/messages")) {
            capabilities.put("registrationProtocol", "anthropic_messages");
            capabilities.put("providerPreset", "anthropic");
        } else if (normalized.contains("generativelanguage.googleapis.com") || normalized.contains(":generatecontent")) {
            capabilities.put("registrationProtocol", "gemini_generatecontent");
            capabilities.put("providerPreset", "gemini");
        } else if (normalized.contains("dashscope") || normalized.contains("bailian")) {
            capabilities.put("registrationProtocol", "bailian_compatible");
            capabilities.put("providerPreset", "bailian");
        } else if (normalized.contains("api.deepseek.com") || normalized.contains("deepseek")) {
            capabilities.put("registrationProtocol", "openai_compatible");
            capabilities.put("providerPreset", "deepseek");
        } else if (normalized.contains("openrouter.ai")) {
            capabilities.put("registrationProtocol", "openai_compatible");
            capabilities.put("providerPreset", "openrouter");
        } else if (normalized.contains("ollama") || normalized.contains("11434")) {
            capabilities.put("registrationProtocol", "openai_compatible");
            capabilities.put("providerPreset", "ollama");
        } else if (normalized.contains("openai.com")) {
            capabilities.put("registrationProtocol", "openai_compatible");
            capabilities.put("providerPreset", "openai");
        } else {
            capabilities.put("registrationProtocol", "openai_compatible");
            capabilities.put("providerPreset", "other_openai");
        }
        return capabilities;
    }

    private String inferRuntimeMode(String type, String source) {
        if ("mcp".equals(type)) {
            String normalized = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("ws://") || normalized.startsWith("wss://")) {
                return "mcp_websocket";
            }
            return "mcp_http";
        }
        if ("agent".equals(type)) {
            return "remote_agent";
        }
        return "prompt_context";
    }

    private String inferDisplayName(String type, String source) {
        if (looksLikeUrl(source)) {
            try {
                URI uri = URI.create(source.trim());
                String host = uri.getHost();
                if (StringUtils.hasText(host)) {
                    return switch (type) {
                        case "mcp" -> "MCP " + host;
                        case "agent" -> "Agent " + host;
                        default -> "Skill " + host;
                    };
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        if ("skill".equals(type)) {
            return "Prompt Skill";
        }
        if ("mcp".equals(type)) {
            return "MCP Capability";
        }
        return "Agent Capability";
    }

    private String inferModelAlias(String source) {
        if (!StringUtils.hasText(source)) {
            return "default-model";
        }
        String adapterId = AgentPlatformAdapterSupport.resolveAdapterId(Map.of(), source, null, null, null);
        if (AgentPlatformAdapterSupport.isPlatformAdapter(adapterId)) {
            return "default-agent";
        }
        String normalized = source.toLowerCase(Locale.ROOT);
        if (normalized.contains("gpt")) {
            return "gpt-4.1";
        }
        if (normalized.contains("anthropic")) {
            return "claude-sonnet";
        }
        if (normalized.contains("gemini")) {
            return "gemini-pro";
        }
        if (normalized.contains("dashscope")) {
            return "qwen-max";
        }
        return "default-model";
    }

    private String generateResourceCode(String displayName, String type) {
        String base = displayName == null ? "" : displayName;
        String ascii = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(ascii)) {
            ascii = type + "-capability";
        }
        if (ascii.length() < 3) {
            ascii = ascii + "-" + type;
        }
        if (ascii.length() > 64) {
            ascii = ascii.substring(0, 64);
        }
        return ascii;
    }

    private boolean looksLikeJson(String source) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        String trimmed = source.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private boolean looksLikeUrl(String source) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        try {
            URI uri = URI.create(source.trim());
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)
                    || "ws".equalsIgnoreCase(scheme)
                    || "wss".equalsIgnoreCase(scheme);
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "agent", "skill", "mcp" -> value;
            default -> null;
        };
    }

    private static String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        return fallback;
    }
}
