package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GrantApplicationVO {
    private Long id;
    private Long applicantId;
    private String applicantName;
    private String resourceType;
    private Long resourceId;
    private String apiKeyId;
    private List<String> actions;
    private String useCase;
    private String callFrequency;
    private String status;
    private Long reviewerId;
    private String reviewerName;
    private String rejectReason;
    private LocalDateTime reviewTime;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
}
