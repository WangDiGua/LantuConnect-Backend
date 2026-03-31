package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DegradationHintVO {
    private String degradationCode;
    private String userFacingHint;
    private String opsHint;
}
