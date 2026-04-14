package com.lantu.connect.gateway.capability.dto;

import com.lantu.connect.gateway.dto.ResourceResolveVO;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CapabilityResolveResultVO {

    private CapabilityDetailVO capability;

    private ResourceResolveVO resolved;

    private Map<String, Object> suggestedPayload;
}
