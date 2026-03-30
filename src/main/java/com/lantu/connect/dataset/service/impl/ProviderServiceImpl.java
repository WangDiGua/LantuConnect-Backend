package com.lantu.connect.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.util.ListQueryKeyword;
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
}
