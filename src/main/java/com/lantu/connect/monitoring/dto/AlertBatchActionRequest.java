package com.lantu.connect.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AlertBatchActionRequest {

    @NotEmpty
    private List<String> ids;

    @NotBlank
    private String action;

    private Long assigneeUserId;

    private String note;
}
