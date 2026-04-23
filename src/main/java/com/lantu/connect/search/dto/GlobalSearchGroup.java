package com.lantu.connect.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchGroup {
    private String key;
    private String title;
    private List<GlobalSearchItem> items;
}
