package com.lantu.connect.sysconfig.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.sysconfig.dto.ModelConfigCreateRequest;
import com.lantu.connect.sysconfig.dto.ModelConfigQueryRequest;
import com.lantu.connect.sysconfig.dto.ModelConfigUpdateRequest;
import com.lantu.connect.sysconfig.entity.ModelConfig;

/**
 * 系统配置ModelConfig服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface ModelConfigService {

    String create(ModelConfigCreateRequest request);

    void update(ModelConfigUpdateRequest request);

    void delete(String id);

    ModelConfig getById(String id);

    PageResult<ModelConfig> page(ModelConfigQueryRequest request);
}
