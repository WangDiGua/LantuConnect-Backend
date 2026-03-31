package com.lantu.connect.dataset.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.dataset.dto.ProviderCreateRequest;
import com.lantu.connect.dataset.dto.ProviderUpdateRequest;
import com.lantu.connect.dataset.entity.Provider;

/**
 * 服务提供商（管理端 CRUD + 分页）.
 */
public interface ProviderService {

    PageResult<Provider> page(int page, int pageSize, String keyword, String status);

    Provider getById(long id);

    Provider create(ProviderCreateRequest request);

    Provider update(long id, ProviderUpdateRequest request);

    void delete(long id);
}
