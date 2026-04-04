package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.sysconfig.dto.QuotaCreateRequest;
import com.lantu.connect.sysconfig.dto.QuotaUpdateRequest;
import com.lantu.connect.sysconfig.entity.Quota;
import com.lantu.connect.sysconfig.mapper.QuotaMapper;
import com.lantu.connect.sysconfig.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * 系统配置Quota服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private static final Set<String> RESOURCE_CATEGORIES = Set.of(
            "all", "agent", "skill", "mcp", "app", "dataset");

    private final QuotaMapper quotaMapper;

    private static String normalizeResourceCategory(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "all";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_CATEGORIES.contains(v)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "resourceCategory 非法: " + raw);
        }
        return v;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QuotaCreateRequest request) {
        Quota entity = new Quota();
        entity.setTargetType(request.getSubjectType());
        Long targetId = null;
        try {
            if (StringUtils.hasText(request.getSubjectId())) {
                targetId = Long.parseLong(request.getSubjectId().trim());
            }
        } catch (NumberFormatException ignored) {
            // 非数字则仅存名称语义
        }
        entity.setTargetId(targetId);
        entity.setTargetName(request.getSubjectName());
        entity.setResourceCategory(normalizeResourceCategory(request.getResourceCategory()));
        entity.setDailyLimit(request.getDailyLimit() != null ? request.getDailyLimit().intValue() : 0);
        entity.setMonthlyLimit(request.getMonthlyLimit() != null ? request.getMonthlyLimit().intValue() : 0);
        entity.setDailyUsed(0);
        entity.setMonthlyUsed(0);
        entity.setEnabled(true);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        quotaMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(QuotaUpdateRequest request) {
        Quota existing = quotaMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (request.getDailyLimit() != null) {
            existing.setDailyLimit(request.getDailyLimit().intValue());
        }
        if (request.getMonthlyLimit() != null) {
            existing.setMonthlyLimit(request.getMonthlyLimit().intValue());
        }
        if (request.getDailyUsed() != null) {
            existing.setDailyUsed(request.getDailyUsed().intValue());
        }
        if (request.getMonthlyUsed() != null) {
            existing.setMonthlyUsed(request.getMonthlyUsed().intValue());
        }
        if (StringUtils.hasText(request.getTargetName())) {
            existing.setTargetName(request.getTargetName().trim());
        }
        if (request.getResourceCategory() != null) {
            existing.setResourceCategory(normalizeResourceCategory(request.getResourceCategory()));
        }
        existing.setUpdateTime(LocalDateTime.now());
        quotaMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (quotaMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        quotaMapper.deleteById(id);
    }

    @Override
    public Quota getById(Long id) {
        Quota quota = quotaMapper.selectById(id);
        if (quota == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return quota;
    }

    @Override
    public PageResult<Quota> page(int page, int pageSize, String subjectType, String keyword, String resourceCategory) {
        Page<Quota> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Quota> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(subjectType)) {
            q.eq(Quota::getTargetType, subjectType);
        }
        if (StringUtils.hasText(resourceCategory)) {
            q.eq(Quota::getResourceCategory, normalizeResourceCategory(resourceCategory));
        }
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            String likeParam = "%" + kw + "%";
            q.and(w -> w.like(Quota::getTargetName, kw)
                    .or()
                    .apply("CAST(target_id AS CHAR) LIKE {0}", likeParam));
        }
        q.orderByDesc(Quota::getUpdateTime);
        Page<Quota> result = quotaMapper.selectPage(p, q);
        return PageResults.from(result);
    }
}
