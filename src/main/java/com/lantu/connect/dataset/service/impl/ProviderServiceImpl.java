package com.lantu.connect.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.dataset.dto.ProviderCreateRequest;
import com.lantu.connect.dataset.dto.ProviderUpdateRequest;
import com.lantu.connect.dataset.entity.Provider;
import com.lantu.connect.dataset.mapper.ProviderMapper;
import com.lantu.connect.dataset.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;

    @Override
    public PageResult<Provider> page(int page, int pageSize, String keyword, String status) {
        Page<Provider> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Provider> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
            q.eq(Provider::getStatus, status.trim());
        }
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            q.and(w -> w.like(Provider::getProviderCode, kw)
                    .or()
                    .like(Provider::getProviderName, kw)
                    .or()
                    .like(Provider::getDescription, kw)
                    .or()
                    .like(Provider::getProviderType, kw));
        }
        q.orderByDesc(Provider::getUpdateTime);
        Page<Provider> result = providerMapper.selectPage(p, q);
        return PageResults.from(result);
    }

    @Override
    public Provider getById(long id) {
        Provider row = providerMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "服务商不存在");
        }
        return row;
    }

    @Override
    public Provider create(ProviderCreateRequest request) {
        String code = request.getProviderCode().trim();
        assertUniqueProviderCode(code, null);
        Provider p = new Provider();
        p.setProviderCode(code);
        p.setProviderName(request.getProviderName().trim());
        p.setProviderType(request.getProviderType().trim());
        p.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        p.setAuthType(request.getAuthType().trim());
        p.setAuthConfig(request.getAuthConfig());
        p.setBaseUrl(StringUtils.hasText(request.getBaseUrl()) ? request.getBaseUrl().trim() : null);
        p.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "active");
        p.setAgentCount(0);
        p.setSkillCount(0);
        providerMapper.insert(p);
        return providerMapper.selectById(p.getId());
    }

    @Override
    public Provider update(long id, ProviderUpdateRequest request) {
        Provider existing = providerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "服务商不存在");
        }
        if (StringUtils.hasText(request.getProviderCode())) {
            String code = request.getProviderCode().trim();
            assertUniqueProviderCode(code, id);
            existing.setProviderCode(code);
        }
        if (StringUtils.hasText(request.getProviderName())) {
            existing.setProviderName(request.getProviderName().trim());
        }
        if (StringUtils.hasText(request.getProviderType())) {
            existing.setProviderType(request.getProviderType().trim());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription().trim());
        }
        if (StringUtils.hasText(request.getAuthType())) {
            existing.setAuthType(request.getAuthType().trim());
        }
        if (request.getAuthConfig() != null) {
            existing.setAuthConfig(request.getAuthConfig());
        }
        if (request.getBaseUrl() != null) {
            existing.setBaseUrl(StringUtils.hasText(request.getBaseUrl()) ? request.getBaseUrl().trim() : null);
        }
        if (StringUtils.hasText(request.getStatus())) {
            existing.setStatus(request.getStatus().trim());
        }
        providerMapper.updateById(existing);
        return providerMapper.selectById(id);
    }

    @Override
    public void delete(long id) {
        Provider existing = providerMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "服务商不存在");
        }
        providerMapper.deleteById(id);
    }

    private void assertUniqueProviderCode(String code, Long excludeId) {
        LambdaQueryWrapper<Provider> w = new LambdaQueryWrapper<Provider>().eq(Provider::getProviderCode, code);
        if (excludeId != null) {
            w.ne(Provider::getId, excludeId);
        }
        if (providerMapper.selectCount(w) > 0) {
            throw new BusinessException(ResultCode.DUPLICATE_NAME, "provider_code 已存在");
        }
    }
}
