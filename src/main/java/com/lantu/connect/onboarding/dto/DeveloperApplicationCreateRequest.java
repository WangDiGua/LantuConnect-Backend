package com.lantu.connect.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeveloperApplicationCreateRequest {

    @NotBlank
    private String contactEmail;

    private String contactPhone;

    private String companyName;

    @NotBlank
    private String applyReason;
}
