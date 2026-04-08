package com.lantu.connect.sysconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.util.ListQueryKeyword;
import com.lantu.connect.common.util.UserDisplayNameResolver;
import com.lantu.connect.sysconfig.dto.AnnouncementBatchUpdateRequest;
import com.lantu.connect.sysconfig.dto.AnnouncementCreateRequest;
import com.lantu.connect.sysconfig.dto.AnnouncementUpdateRequest;
import com.lantu.connect.sysconfig.entity.Announcement;
import com.lantu.connect.sysconfig.mapper.AnnouncementMapper;
import com.lantu.connect.sysconfig.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final UserDisplayNameResolver userDisplayNameResolver;

    @Override
    public PageResult<Announcement> list(Integer page, Integer pageSize, String keyword, String type) {
        int p = page == null || page < 1 ? 1 : page;
        int ps = pageSize == null || pageSize < 1 ? 20 : pageSize;
        Page<Announcement> mp = new Page<>(p, ps);
        LambdaQueryWrapper<Announcement> q = new LambdaQueryWrapper<Announcement>()
                .eq(StringUtils.hasText(type), Announcement::getType, type != null ? type.trim() : null)
                .orderByDesc(Announcement::getPinned)
                .orderByDesc(Announcement::getCreateTime);
        String kw = ListQueryKeyword.normalize(keyword);
        if (kw != null) {
            q.and(w -> w.like(Announcement::getTitle, kw)
                    .or()
                    .like(Announcement::getSummary, kw)
                    .or()
                    .like(Announcement::getContent, kw));
        }
        Page<Announcement> result = announcementMapper.selectPage(mp, q);
        enrichNames(result.getRecords());
        return PageResults.from(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Announcement create(Long operatorUserId, AnnouncementCreateRequest request) {
        Announcement entity = new Announcement();
        entity.setTitle(request.getTitle());
        entity.setSummary(request.getSummary());
        entity.setContent(request.getContent());
        entity.setType(StringUtils.hasText(request.getType()) ? request.getType() : "notice");
        entity.setPinned(request.getPinned() != null ? request.getPinned() : false);
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE);
        entity.setCreatedBy(operatorUserId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
        announcementMapper.insert(entity);
        entity.setCreatedByName(userDisplayNameResolver.resolveDisplayName(operatorUserId));
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AnnouncementUpdateRequest request) {
        Announcement entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在");
        }
        if (StringUtils.hasText(request.getTitle())) {
            entity.setTitle(request.getTitle());
        }
        if (StringUtils.hasText(request.getSummary())) {
            entity.setSummary(request.getSummary());
        }
        if (request.getContent() != null) {
            entity.setContent(request.getContent());
        }
        if (StringUtils.hasText(request.getType())) {
            entity.setType(request.getType());
        }
        if (request.getPinned() != null) {
            entity.setPinned(request.getPinned());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        entity.setUpdateTime(LocalDateTime.now());
        announcementMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(AnnouncementBatchUpdateRequest body) {
        if (body.getIds() == null || body.getIds().isEmpty()) {
            return;
        }
        AnnouncementUpdateRequest patch = new AnnouncementUpdateRequest();
        BeanUtils.copyProperties(body, patch, "ids");
        for (Long id : body.getIds()) {
            update(id, patch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (announcementMapper.selectById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "公告不存在");
        }
        announcementMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            delete(id);
        }
    }

    private void enrichNames(java.util.List<Announcement> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        java.util.Map<Long, String> names = userDisplayNameResolver.resolveDisplayNames(
                records.stream().map(Announcement::getCreatedBy).toList());
        records.forEach(item -> item.setCreatedByName(names.get(item.getCreatedBy())));
    }
}
