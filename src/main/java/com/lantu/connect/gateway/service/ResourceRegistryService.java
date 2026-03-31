package com.lantu.connect.gateway.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.dto.LifecycleTimelineVO;
import com.lantu.connect.gateway.dto.ObservabilitySummaryVO;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ResourceVersionCreateRequest;
import com.lantu.connect.gateway.dto.ResourceVersionVO;

import java.util.List;

public interface ResourceRegistryService {

    ResourceManageVO create(Long operatorUserId, ResourceUpsertRequest request);

    ResourceManageVO update(Long operatorUserId, Long resourceId, ResourceUpsertRequest request);

    void delete(Long operatorUserId, Long resourceId);

    ResourceManageVO submitForAudit(Long operatorUserId, Long resourceId);

    ResourceManageVO deprecate(Long operatorUserId, Long resourceId);

    PageResult<ResourceManageVO> pageMine(Long operatorUserId, String resourceType, String status,
                                          String keyword, String sortBy, String sortOrder,
                                          Integer page, Integer pageSize);

    ResourceVersionVO createVersion(Long operatorUserId, Long resourceId, ResourceVersionCreateRequest request);

    ResourceManageVO switchVersion(Long operatorUserId, Long resourceId, String version);

    List<ResourceVersionVO> listVersions(Long operatorUserId, Long resourceId);

    ResourceManageVO withdraw(Long operatorUserId, Long resourceId);

    ResourceManageVO getById(Long operatorUserId, Long resourceId);

    /**
     * 根据 DB 中当前扩展表重建默认版本快照（用于技能包上传等与请求体无关的变更）。
     */
    void recomputeCurrentVersionSnapshot(Long operatorUserId, Long resourceId);

    LifecycleTimelineVO lifecycleTimeline(Long operatorUserId, Long resourceId);

    ObservabilitySummaryVO observabilitySummary(Long operatorUserId, String resourceType, Long resourceId);
}

