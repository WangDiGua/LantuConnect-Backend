package com.lantu.connect.gateway.capability.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityImportRequest {

    @NotBlank
    private String source;

    private String preferredType;

    private String displayName;

    private String description;
}
