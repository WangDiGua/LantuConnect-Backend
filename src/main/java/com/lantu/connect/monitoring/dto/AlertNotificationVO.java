package com.lantu.connect.monitoring.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AlertNotificationVO {

    private Long id;
    private Long userId;
    private String title;
    private String body;
    private String severity;
    private Boolean read;
    private LocalDateTime createTime;
}
