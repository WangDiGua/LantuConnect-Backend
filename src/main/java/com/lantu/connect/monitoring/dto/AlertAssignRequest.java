package com.lantu.connect.monitoring.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AlertAssignRequest {

    @NotNull
    private Long assigneeUserId;

    private String note;
}
