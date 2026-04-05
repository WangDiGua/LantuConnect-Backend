package com.lantu.connect.gateway.dto;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 资源解析结果中的 {@code spec} 脱敏：避免 token/密码/自定义头等经 API 泄露到前端。
 */
public final class ResourceResolveSpecSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> DROP_KEYS_LOWER = Set.of(
            "token", "bearertoken", "accesstoken", "access_token", "password", "clientsecret", "client_secret",
            "apikey", "api_key", "authorization"
    );
    private static final Set<String> DROP_SUFFIX_LOWER = Set.of(
            "secretref", "secret_ref"
    );

    private ResourceResolveSpecSanitizer() {
    }

    public static ResourceResolveVO sanitize(ResourceResolveVO source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> spec = source.getSpec();
        ResourceResolveVO.ResourceResolveVOBuilder b = ResourceResolveVO.builder()
                .resourceType(source.getResourceType())
                .resourceId(source.getResourceId())
                .version(source.getVersion())
                .resourceCode(source.getResourceCode())
                .displayName(source.getDisplayName())
                .status(source.getStatus())
                .createdBy(source.getCreatedBy())
                .createdByName(source.getCreatedByName())
                .invokeType(source.getInvokeType())
                .endpoint(source.getEndpoint())
                .spec(spec == null ? null : sanitizeSpec(spec))
                .serviceDetailMd(source.getServiceDetailMd())
                .launchToken(source.getLaunchToken())
                .launchUrl(source.getLaunchUrl())
                .tags(source.getTags())
                .observability(source.getObservability())
                .quality(source.getQuality());
        return b.build();
    }

    public static Map<String, Object> sanitizeSpec(Map<String, Object> spec) {
        if (spec == null || spec.isEmpty()) {
            return spec;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : spec.entrySet()) {
            String k = e.getKey();
            if (k == null) {
                continue;
            }
            String kl = k.toLowerCase(Locale.ROOT);
            if (shouldDropKey(kl)) {
                continue;
            }
            Object v = e.getValue();
            if ("headers".equals(kl) && v instanceof Map<?, ?> hm) {
                Map<String, Object> nh = new LinkedHashMap<>();
                for (var he : hm.entrySet()) {
                    String hk = he.getKey() == null ? null : String.valueOf(he.getKey());
                    if (hk != null) {
                        nh.put(hk, REDACTED);
                    }
                }
                out.put(k, nh);
                continue;
            }
            if (v instanceof Map<?, ?> nested) {
                Map<String, Object> copy = new LinkedHashMap<>();
                nested.forEach((nk, nv) -> {
                    if (nk != null) {
                        copy.put(String.valueOf(nk), nv);
                    }
                });
                out.put(k, sanitizeSpec(copy));
                continue;
            }
            out.put(k, v);
        }
        return out;
    }

    private static boolean shouldDropKey(String kl) {
        if (DROP_KEYS_LOWER.contains(kl)) {
            return true;
        }
        for (String suf : DROP_SUFFIX_LOWER) {
            if (kl.endsWith(suf)) {
                return true;
            }
        }
        return false;
    }
}
