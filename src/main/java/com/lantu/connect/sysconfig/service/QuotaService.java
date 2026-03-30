package com.lantu.connect.sysconfig.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.sysconfig.dto.QuotaCreateRequest;
import com.lantu.connect.sysconfig.dto.QuotaUpdateRequest;
import com.lantu.connect.sysconfig.entity.Quota;

/**
 * 系统配置Quota服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface QuotaService {

    Long create(QuotaCreateRequest request);

    void update(QuotaUpdateRequest request);

    void delete(Long id);

    Quota getById(Long id);

    PageResult<Quota> page(int page, int pageSize, String subjectType, String keyword);
}
