package com.lantu.connect.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DeveloperApplicationBatchRejectRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;

    @NotBlank
    private String reviewComment;
}
