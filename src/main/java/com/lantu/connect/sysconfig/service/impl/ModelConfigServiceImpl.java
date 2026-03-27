package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.dto.ModelConfigCreateRequest;
import com.lantu.connect.sysconfig.dto.ModelConfigQueryRequest;
import com.lantu.connect.sysconfig.dto.ModelConfigUpdateRequest;
import com.lantu.connect.sysconfig.entity.ModelConfig;
import com.lantu.connect.sysconfig.mapper.ModelConfigMapper;
import com.lantu.connect.sysconfig.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 系统配置ModelConfig服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ModelConfigCreateRequest request) {
        ModelConfig entity = new ModelConfig();
        entity.setName(request.getName());
        entity.setModelId(request.getModelId());
        entity.setProvider(request.getProvider());
        entity.setEndpoint(request.getEndpoint());
        entity.setApiKey(request.getApiKey());
        entity.setMaxTokens(request.getMaxTokens());
        entity.setTemperature(request.getTemperature());
        entity.setTopP(request.getTopP());
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        entity.setRateLimit(request.getRateLimit());
        entity.setCostPerToken(request.getCostPerToken());
        entity.setDescription(request.getDescription());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        modelConfigMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(ModelConfigUpdateRequest request) {
        ModelConfig existing = modelConfigMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getModelId() != null) {
            existing.setModelId(request.getModelId());
        }
        if (request.getProvider() != null) {
            existing.setProvider(request.getProvider());
        }
        if (request.getEndpoint() != null) {
            existing.setEndpoint(request.getEndpoint());
        }
        if (request.getApiKey() != null) {
            existing.setApiKey(request.getApiKey());
        }
        if (request.getMaxTokens() != null) {
            existing.setMaxTokens(request.getMaxTokens());
        }
        if (request.getTemperature() != null) {
            existing.setTemperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            existing.setTopP(request.getTopP());
        }
        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        if (request.getRateLimit() != null) {
            existing.setRateLimit(request.getRateLimit());
        }
        if (request.getCostPerToken() != null) {
            existing.setCostPerToken(request.getCostPerToken());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        existing.setUpdateTime(LocalDateTime.now());
        modelConfigMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (modelConfigMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        modelConfigMapper.deleteById(id);
    }

    @Override
    public ModelConfig getById(String id) {
        ModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return config;
    }

    @Override
    public PageResult<ModelConfig> page(ModelConfigQueryRequest request) {
        Page<ModelConfig> page = new Page<>(request.getPage(), request.getPageSize());
        LambdaQueryWrapper<ModelConfig> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getName())) {
            q.like(ModelConfig::getName, request.getName());
        }
        if (StringUtils.hasText(request.getProvider())) {
            q.eq(ModelConfig::getProvider, request.getProvider());
        }
        q.orderByDesc(ModelConfig::getUpdateTime);
        Page<ModelConfig> result = modelConfigMapper.selectPage(page, q);
        return PageResults.from(result);
    }
}
