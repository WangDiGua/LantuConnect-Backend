package com.lantu.connect.gateway.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.gateway.dto.ResourceCatalogItemVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.gateway.dto.ResourceResolveRequest;
import com.lantu.connect.gateway.dto.ResourceResolveVO;
import com.lantu.connect.gateway.dto.ResourceStatsVO;
import com.lantu.connect.gateway.dto.SearchSuggestion;
import com.lantu.connect.dashboard.dto.ExploreHubData;
import com.lantu.connect.usermgmt.entity.ApiKey;

import java.util.List;

public interface UnifiedGatewayService {

    PageResult<ResourceCatalogItemVO> catalog(ResourceCatalogQueryRequest request, ApiKey apiKey, Long userId);

    ResourceResolveVO resolve(ResourceResolveRequest request, ApiKey apiKey, Long userId);

    ResourceResolveVO getByTypeAndId(String resourceType, String resourceId, ApiKey apiKey, Long userId);

    InvokeResponse invoke(Long userId, String traceId, String ip, InvokeRequest request, ApiKey apiKey);

    ResourceStatsVO getResourceStats(String resourceType, String resourceId);

    List<ExploreHubData.ExploreResourceItem> trending(String resourceType, Integer limit);

    List<SearchSuggestion> searchSuggestions(String query);
}
