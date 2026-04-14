package com.lantu.connect.gateway.capability;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.capability.dto.CapabilityCreateRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityDetailVO;
import com.lantu.connect.gateway.capability.dto.CapabilityImportRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityImportSuggestionVO;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityInvokeResultVO;
import com.lantu.connect.gateway.capability.dto.CapabilityResolveRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityResolveResultVO;
import com.lantu.connect.gateway.capability.dto.CapabilitySummaryVO;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionRequest;
import com.lantu.connect.gateway.capability.dto.CapabilityToolSessionVO;
import com.lantu.connect.gateway.dto.ResourceCatalogQueryRequest;
import com.lantu.connect.usermgmt.entity.ApiKey;

public interface CapabilityV2Service {

    CapabilityImportSuggestionVO detect(CapabilityImportRequest request);

    CapabilityDetailVO create(Long operatorUserId, CapabilityCreateRequest request);

    PageResult<CapabilitySummaryVO> list(ResourceCatalogQueryRequest request, ApiKey apiKey, Long userId);

    CapabilityDetailVO getById(Long capabilityId, String include, ApiKey apiKey, Long userId);

    CapabilityResolveResultVO resolve(Long capabilityId, CapabilityResolveRequest request, ApiKey apiKey, Long userId);

    CapabilityInvokeResultVO invoke(Long capabilityId, Long userId, String traceId, String ip, CapabilityInvokeRequest request, ApiKey apiKey);

    CapabilityToolSessionVO toolSession(Long capabilityId, Long userId, String traceId, String ip, CapabilityToolSessionRequest request, ApiKey apiKey);
}
