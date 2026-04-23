package com.lantu.connect.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchItem {
    private String id;
    private String kind;
    private String title;
    private String subtitle;
    private String description;
    private String badge;
    private String resourceType;
    private String resourceId;
    private String path;
    private Integer score;
}
