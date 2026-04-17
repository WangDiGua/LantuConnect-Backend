package com.lantu.connect.usermgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDetailResponse {

    private String id;
    private String name;
    private String prefix;
    private String maskedKey;
    private List<String> scopes;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private Long callCount;
    private String createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private String integrationPackageId;
    private String secretPlain;
    private Boolean secretAvailable;
}
