package com.lantu.connect.gateway.protocol;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProtocolInvokerRegistry {

    private final List<GatewayProtocolInvoker> invokers;

    public ProtocolInvokeResult invoke(String protocol,
                                       String endpoint,
                                       int timeoutSec,
                                       String traceId,
                                       Map<String, Object> payload,
                                       Map<String, Object> spec,
                                       ProtocolInvokeContext ctx) throws Exception {
        GatewayProtocolInvoker invoker = getInvoker(protocol);
        return invoker.invoke(endpoint, timeoutSec, traceId, payload, spec, ctx);
    }

    public boolean isSupported(String protocol) {
        try {
            getInvoker(protocol);
            return true;
        } catch (BusinessException e) {
            return false;
        }
    }

    private GatewayProtocolInvoker getInvoker(String protocol) {
        String p = StringUtils.hasText(protocol) ? protocol.trim().toLowerCase(Locale.ROOT) : "http";
        for (GatewayProtocolInvoker invoker : invokers) {
            if (invoker.supports(p)) {
                return invoker;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "暂不支持的调用协议: " + protocol);
    }
}
