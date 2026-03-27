package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ResourceGrantVO {
    private Long id;
    private String resourceType;
    private Long resourceId;
    private String granteeType;
    private String granteeId;
    private List<String> actions;
    private String status;
    private Long grantedByUserId;
    private String grantedByName;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
