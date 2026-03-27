package com.lantu.connect.monitoring.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.monitoring.entity.HealthConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 监控 HealthConfig 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface HealthConfigMapper extends BaseMapper<HealthConfig> {
}
