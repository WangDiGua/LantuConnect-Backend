package com.lantu.connect.gateway.capability.dto;

import com.lantu.connect.gateway.dto.InvokeResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CapabilityInvokeResultVO {

    private CapabilityDetailVO capability;

    private InvokeResponse response;
}
