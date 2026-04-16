package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertRuleScopeResourceOptionVO {

    private Long id;
    private String resourceType;
    private String displayName;
}
