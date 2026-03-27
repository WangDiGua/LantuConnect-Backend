package com.lantu.connect.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestion {

    private String text;
    private String resourceType;
    private String resourceId;
    private String highlightedText;
}
