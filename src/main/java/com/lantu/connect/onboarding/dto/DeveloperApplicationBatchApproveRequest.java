package com.lantu.connect.onboarding.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DeveloperApplicationBatchApproveRequest {

    @NotEmpty
    @Size(max = 200)
    private List<Long> ids;

    /** 可选审批备注 */
    private String reviewComment;
}
