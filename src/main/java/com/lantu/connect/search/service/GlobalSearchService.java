package com.lantu.connect.search.service;

import com.lantu.connect.search.dto.GlobalSearchResponse;

public interface GlobalSearchService {
    GlobalSearchResponse search(Long userId, String query, String scope, Integer limitPerGroup);
}
