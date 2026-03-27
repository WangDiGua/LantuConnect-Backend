package com.lantu.connect.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GrantApplicationRequest {

    @NotBlank(message = "resourceType 不能为空")
    private String resourceType;

    @NotNull(message = "resourceId 不能为空")
    private Long resourceId;

    @NotBlank(message = "apiKeyId 不能为空")
    private String apiKeyId;

    @NotEmpty(message = "actions 不能为空")
    private List<String> actions;

    private String useCase;
    private String callFrequency;
    private LocalDateTime expiresAt;
}
