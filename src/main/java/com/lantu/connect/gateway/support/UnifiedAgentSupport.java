package com.lantu.connect.gateway.support;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class UnifiedAgentSupport {

    public static final String UNIFIED_AGENT_EXPOSURE = "unified_agent";
    public static final String DELIVERY_MODE_API = "api";
    public static final String DELIVERY_MODE_PAGE = "page";

    private UnifiedAgentSupport() {
    }

    public static boolean isUnifiedAgentExposure(String raw) {
        return UNIFIED_AGENT_EXPOSURE.equals(normalize(raw));
    }

    public static boolean shouldAppearInAgentView(String resourceType, String agentExposure) {
        String type = normalize(resourceType);
        if ("agent".equals(type)) {
            return true;
        }
        return "app".equals(type) && isUnifiedAgentExposure(agentExposure);
    }

    public static boolean shouldAppearInAppView(String resourceType, String agentExposure) {
        return "app".equals(normalize(resourceType)) && !isUnifiedAgentExposure(agentExposure);
    }

    public static boolean matchesRequestedType(String requestedType, String actualResourceType, String agentExposure) {
        String requested = normalize(requestedType);
        if (!StringUtils.hasText(requested)) {
            return true;
        }
        return switch (requested) {
            case "agent" -> shouldAppearInAgentView(actualResourceType, agentExposure);
            case "app" -> shouldAppearInAppView(actualResourceType, agentExposure);
            default -> requested.equals(normalize(actualResourceType));
        };
    }

    public static String resolveViewType(String requestedType, String actualResourceType, String agentExposure) {
        String requested = normalize(requestedType);
        if ("agent".equals(requested) && shouldAppearInAgentView(actualResourceType, agentExposure)) {
            return "agent";
        }
        if ("app".equals(requested) && "app".equals(normalize(actualResourceType))) {
            return "app";
        }
        return normalize(actualResourceType);
    }

    public static String resolveDeliveryMode(String resourceType, String agentExposure) {
        return shouldAppearInAgentView(resourceType, agentExposure) && "app".equals(normalize(resourceType))
                ? DELIVERY_MODE_PAGE
                : DELIVERY_MODE_API;
    }

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
