package com.lantu.connect.gateway.capability.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CapabilityToolItemVO {

    private String name;

    private String description;

    private Map<String, Object> parameters;
}
