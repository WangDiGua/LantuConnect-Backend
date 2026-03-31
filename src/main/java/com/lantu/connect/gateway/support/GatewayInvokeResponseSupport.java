package com.lantu.connect.gateway.support;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.InvokeResponse;
import org.springframework.http.HttpStatus;

/**
 * 将 {@link InvokeResponse} 映射为 HTTP 状态与统一包体 {@link R}，便于 AccessLog 与监控识别上游失败。
 */
public final class GatewayInvokeResponseSupport {

    private GatewayInvokeResponseSupport() {
    }

    public static boolean isInvokeSuccess(InvokeResponse resp) {
        return resp != null && "success".equalsIgnoreCase(resp.getStatus());
    }

    /** 上游业务失败时：优先使用 {@link InvokeResponse#getStatusCode()} 在 400–599 内的值，否则 502。 */
    public static HttpStatus toHttpStatus(InvokeResponse resp) {
        if (isInvokeSuccess(resp)) {
            return HttpStatus.OK;
        }
        Integer sc = resp != null ? resp.getStatusCode() : null;
        if (sc != null && sc >= 400 && sc <= 599) {
            try {
                return HttpStatus.valueOf(sc);
            } catch (IllegalArgumentException ignored) {
                return HttpStatus.BAD_GATEWAY;
            }
        }
        return HttpStatus.BAD_GATEWAY;
    }

    public static R<InvokeResponse> wrap(InvokeResponse resp) {
        if (isInvokeSuccess(resp)) {
            return R.ok(resp);
        }
        String msg = summarizeFailure(resp);
        return R.of(ResultCode.EXTERNAL_SERVICE_ERROR.getCode(), msg, resp);
    }

    private static String summarizeFailure(InvokeResponse resp) {
        String base = ResultCode.EXTERNAL_SERVICE_ERROR.getMessage();
        if (resp == null) {
            return base;
        }
        String body = resp.getBody();
        if (body == null || body.isBlank()) {
            return base;
        }
        String t = body.strip().replace('\r', ' ').replace('\n', ' ');
        int cap = 200;
        if (t.length() <= cap) {
            return base + ": " + t;
        }
        return base + ": " + t.substring(0, cap) + "...";
    }
}
