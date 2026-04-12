package com.lantu.connect.integrationpackage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntegrationPackageItemDTO {

    @NotBlank
    private String resourceType;

    @NotNull
    private Long resourceId;
}
