package com.lantu.connect.dataset.service;

import com.lantu.connect.dataset.dto.TagCreateRequest;
import com.lantu.connect.dataset.dto.TagUpdateRequest;
import com.lantu.connect.dataset.entity.Tag;

import java.util.List;

/**
 * 数据集Tag服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface TagService {

    List<Tag> list();

    Long create(TagCreateRequest request);

    void delete(Long id);

    List<Long> batchCreate(List<TagCreateRequest> requests);

    void update(Long id, TagUpdateRequest request);
}
