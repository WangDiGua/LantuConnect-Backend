package com.lantu.connect.sysconfig.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.sysconfig.dto.AnnouncementCreateRequest;
import com.lantu.connect.sysconfig.dto.AnnouncementUpdateRequest;
import com.lantu.connect.sysconfig.dto.AnnouncementBatchUpdateRequest;
import com.lantu.connect.sysconfig.entity.Announcement;

import java.util.List;

public interface AnnouncementService {

    PageResult<Announcement> list(Integer page, Integer pageSize, String keyword, String type);

    Announcement create(Long operatorUserId, AnnouncementCreateRequest request);

    void update(Long id, AnnouncementUpdateRequest request);

    void batchUpdate(AnnouncementBatchUpdateRequest body);

    void delete(Long id);

    void batchDelete(List<Long> ids);
}
