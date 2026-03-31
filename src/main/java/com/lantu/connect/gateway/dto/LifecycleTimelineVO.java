package com.lantu.connect.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LifecycleTimelineVO {

    private Long resourceId;
    private String resourceType;
    private String resourceCode;
    private String displayName;
    private String currentStatus;
    private List<Event> events;

    @Data
    @Builder
    public static class Event {
        private String eventType;
        private String title;
        private String status;
        private String actor;
        private String reason;
        private LocalDateTime eventTime;
    }
}
