package com.lantu.connect.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreHubData {

    private Map<String, Object> platformStats;
    private List<ExploreResourceItem> trendingResources;
    private List<ExploreResourceItem> recentPublished;
    private List<ExploreResourceItem> recommendedForUser;
    private List<AnnouncementItem> announcements;
    private List<ContributorItem> topContributors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExploreResourceItem {
        private String resourceType;
        private String resourceId;
        private String resourceCode;
        private String displayName;
        private String description;
        private String status;
        private Long callCount;
        private Long favoriteCount;
        private Long reviewCount;
        private Double rating;
        private String reason;
        private LocalDateTime publishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnnouncementItem {
        private Long id;
        private String title;
        private String summary;
        private String type;
        private Boolean pinned;
        private LocalDateTime createdAt;
        private LocalDateTime createTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributorItem {
        private Long userId;
        private String username;
        private String avatar;
        private Long resourceCount;
        private Long totalCalls;
        private Long weeklyNewResources;
        private Long weeklyCalls;
        private Long likeCount;
    }
}
