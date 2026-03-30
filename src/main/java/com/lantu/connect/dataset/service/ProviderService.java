package com.lantu.connect.dataset.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.dataset.entity.Provider;

/**
 * 服务提供商（管理端列表）.
 */
public interface ProviderService {

    PageResult<Provider> page(int page, int pageSize, String keyword, String status);
}
