package com.lantu.connect.gateway.mapper;

import com.lantu.connect.gateway.dto.SkillExternalKeyLongCount;
import com.lantu.connect.gateway.dto.SkillExternalReviewAggRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SkillExternalEngagementAggMapper {

    List<SkillExternalKeyLongCount> selectFavoriteCounts(@Param("keys") List<String> keys);

    List<SkillExternalKeyLongCount> selectDownloadCounts(@Param("keys") List<String> keys);

    List<SkillExternalKeyLongCount> selectViewCounts(@Param("keys") List<String> keys);

    List<SkillExternalReviewAggRow> selectReviewAgg(@Param("keys") List<String> keys);
}
