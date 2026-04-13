package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AgentKeyMetaVO {
    private String id;
    private String maskedKey;
    private String status;
    private List<String> scopes;
    private LocalDateTime createTime;
    private LocalDateTime lastUsedAt;
}

