package com.lantu.connect.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Unified platform event model for async processing via message queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformEvent implements Serializable {

    private String eventType;
    private Long userId;
    private String resourceType;
    private Long resourceId;
    private String displayName;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;

    public static final String EVENT_RESOURCE_SUBMITTED = "resource.submitted";
    public static final String EVENT_RESOURCE_APPROVED = "resource.approved";
    public static final String EVENT_RESOURCE_REJECTED = "resource.rejected";
    public static final String EVENT_RESOURCE_PUBLISHED = "resource.published";
    public static final String EVENT_GRANT_APPLIED = "grant.applied";
    public static final String EVENT_GRANT_APPROVED = "grant.approved";
    public static final String EVENT_GRANT_REJECTED = "grant.rejected";
}
