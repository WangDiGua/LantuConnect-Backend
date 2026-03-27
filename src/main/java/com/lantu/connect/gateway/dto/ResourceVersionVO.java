package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResourceVersionVO {

    private Long id;
    private Long resourceId;
    private String version;
    private String status;
    private Boolean current;
    private LocalDateTime createTime;
}

