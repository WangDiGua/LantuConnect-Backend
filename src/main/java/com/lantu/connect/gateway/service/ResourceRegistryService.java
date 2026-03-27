package com.lantu.connect.gateway.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.gateway.dto.ResourceManageVO;
import com.lantu.connect.gateway.dto.ResourceUpsertRequest;
import com.lantu.connect.gateway.dto.ResourceVersionCreateRequest;
import com.lantu.connect.gateway.dto.ResourceVersionVO;

import java.util.List;

public interface ResourceRegistryService {

    ResourceManageVO create(Long operatorUserId, ResourceUpsertRequest request);

    ResourceManageVO update(Long operatorUserId, Long resourceId, ResourceUpsertRequest request);

    void delete(Long operatorUserId, Long resourceId);

    void submitForAudit(Long operatorUserId, Long resourceId);

    void deprecate(Long operatorUserId, Long resourceId);

    PageResult<ResourceManageVO> pageMine(Long operatorUserId, String resourceType, Integer page, Integer pageSize);

    ResourceVersionVO createVersion(Long operatorUserId, Long resourceId, ResourceVersionCreateRequest request);

    void switchVersion(Long operatorUserId, Long resourceId, String version);

    List<ResourceVersionVO> listVersions(Long operatorUserId, Long resourceId);

    void withdraw(Long operatorUserId, Long resourceId);

    ResourceManageVO getById(Long operatorUserId, Long resourceId);
}

