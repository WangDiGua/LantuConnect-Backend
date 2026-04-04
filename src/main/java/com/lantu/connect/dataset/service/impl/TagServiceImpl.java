package com.lantu.connect.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.dataset.dto.TagCreateRequest;
import com.lantu.connect.dataset.dto.TagUpdateRequest;
import com.lantu.connect.dataset.entity.Tag;
import com.lantu.connect.dataset.mapper.TagMapper;
import com.lantu.connect.dataset.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 数据集Tag服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<Tag> list() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TagCreateRequest request) {
        Tag entity = new Tag();
        entity.setName(request.getName().trim());
        entity.setCategory(resolveCategory(request.getCategory()));
        entity.setUsageCount(0);
        tagMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (tagMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        tagMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreate(List<TagCreateRequest> requests) {
        if (CollectionUtils.isEmpty(requests)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (TagCreateRequest request : requests) {
            Tag entity = new Tag();
            entity.setName(request.getName().trim());
            entity.setCategory(resolveCategory(request.getCategory()));
            entity.setUsageCount(0);
            tagMapper.insert(entity);
            ids.add(entity.getId());
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TagUpdateRequest request) {
        Tag entity = tagMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "标签不存在");
        }
        if (StringUtils.hasText(request.getName())) {
            entity.setName(request.getName());
        }
        if (request.getCategory() != null) {
            entity.setCategory(resolveCategory(request.getCategory()));
        }
        tagMapper.updateById(entity);
    }

    /**
     * 与统一资源五类对齐：agent/skill/mcp/app/dataset；general 为通用标签。
     * 兼容管理端中文分类名。
     */
    private static String resolveCategory(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "general";
        }
        String s = raw.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "agent", "skill", "mcp", "app", "dataset", "general" -> lower;
            case "应用" -> "app";
            case "数据集" -> "dataset";
            case "通用" -> "general";
            default -> "general";
        };
    }
}
