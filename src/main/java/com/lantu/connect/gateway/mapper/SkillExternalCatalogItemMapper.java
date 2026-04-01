package com.lantu.connect.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.gateway.entity.SkillExternalCatalogItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SkillExternalCatalogItemMapper extends BaseMapper<SkillExternalCatalogItem> {

    void upsertBatch(@Param("list") List<SkillExternalCatalogItem> list);

    int deleteWhereSyncBatchLt(@Param("batch") long batch);

    long countKeyword(@Param("keyword") String keyword);

    List<SkillExternalCatalogItem> selectKeywordPage(@Param("keyword") String keyword,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);
}
