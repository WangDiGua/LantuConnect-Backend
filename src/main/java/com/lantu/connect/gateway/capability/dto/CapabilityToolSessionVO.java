package com.lantu.connect.gateway.capability.dto;

import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ToolDispatchRouteVO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CapabilityToolSessionVO {

    private Long capabilityId;

    private String capabilityType;

    private String action;

    private List<CapabilityToolItemVO> tools;

    private List<ToolDispatchRouteVO> routes;

    private List<String> warnings;

    private InvokeResponse toolCallResponse;
}
