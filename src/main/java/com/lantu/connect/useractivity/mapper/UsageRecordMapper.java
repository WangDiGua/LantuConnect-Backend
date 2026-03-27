package com.lantu.connect.useractivity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.useractivity.entity.UsageRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户活动 UsageRecord 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface UsageRecordMapper extends BaseMapper<UsageRecord> {
}
