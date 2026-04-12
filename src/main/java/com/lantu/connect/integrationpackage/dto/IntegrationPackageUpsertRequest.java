package com.lantu.connect.integrationpackage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class IntegrationPackageUpsertRequest {

    @NotBlank
    private String name;

    private String description;

    /** 默认 active */
    private String status;

    private List<IntegrationPackageItemDTO> items;
}
