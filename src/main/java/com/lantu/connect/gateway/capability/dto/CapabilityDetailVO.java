package com.lantu.connect.gateway.capability.dto;

import com.lantu.connect.gateway.dto.ResourceSummaryVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CapabilityDetailVO {

    private Long capabilityId;

    private String capabilityType;

    private String displayName;

    private String resourceCode;

    private String status;

    private String version;

    private String runtimeMode;

    private String invokeMode;

    private String invokeType;

    private String endpoint;

    private String serviceDetailMd;

    private Boolean callable;

    private Map<String, Object> inputSchema;

    private Map<String, Object> defaults;

    private Map<String, Object> authRefs;

    private Map<String, Object> capabilities;

    private List<ResourceSummaryVO> bindingClosure;
}
