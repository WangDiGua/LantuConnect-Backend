package com.lantu.connect.monitoring.probe;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class ResourceProbeEngine {

    private final List<ResourceProbeHandler> handlers;

    public ResourceProbeEngine(List<ResourceProbeHandler> handlers) {
        this.handlers = handlers;
    }

    public ResourceProbeResult probe(ResourceProbeTarget target) {
        String resourceType = target == null ? null : target.resourceType();
        if (!StringUtils.hasText(resourceType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resource type is required");
        }
        String normalized = resourceType.trim().toLowerCase(Locale.ROOT);
        for (ResourceProbeHandler handler : handlers) {
            if (handler.supports(normalized)) {
                return handler.probe(target);
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "unsupported health probe type: " + normalized);
    }
}
