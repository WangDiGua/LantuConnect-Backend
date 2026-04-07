package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SkillExternalItemKeyRequest {
    @NotBlank
    private String itemKey;
}
