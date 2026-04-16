package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AlertRuleScopeOptionVO {

    private List<String> resourceTypes;
    private List<AlertRuleScopeResourceOptionVO> resources;
}
