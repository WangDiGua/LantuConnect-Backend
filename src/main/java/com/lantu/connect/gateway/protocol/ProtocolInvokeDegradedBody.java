package com.lantu.connect.gateway.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resilience4j fallback 与调用日志共用的降级响应体（含 {@link BusinessException} 的业务码与文案）。
 */
public final class ProtocolInvokeDegradedBody {

    private ProtocolInvokeDegradedBody() {
    }

    public static String buildJson(ObjectMapper objectMapper, String label, Throwable t) {
        try {
            return objectMapper.writeValueAsString(degradedMap(label, t));
        } catch (JsonProcessingException e) {
            return "{\"error\":\"" + escapeJson(label) + "\",\"message\":\"failed to serialize degraded details\"}";
        }
    }

    static Map<String, Object> degradedMap(String label, Throwable t) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", label);
        if (t == null) {
            err.put("cause", "null");
            return err;
        }
        err.put("cause", t.getClass().getSimpleName());
        BusinessException be = unwrapBusiness(t);
        if (be != null) {
            err.put("businessCode", be.getCode());
            String msg = be.getMessage();
            err.put("message", msg != null ? msg : "");
        } else {
            String msg = t.getMessage();
            err.put("message", msg != null ? msg : "");
        }
        return err;
    }

    private static BusinessException unwrapBusiness(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof BusinessException be) {
                return be;
            }
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
