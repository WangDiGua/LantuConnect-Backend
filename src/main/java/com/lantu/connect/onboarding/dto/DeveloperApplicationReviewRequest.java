package com.lantu.connect.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeveloperApplicationReviewRequest {

    @NotBlank
    private String reviewComment;
}
