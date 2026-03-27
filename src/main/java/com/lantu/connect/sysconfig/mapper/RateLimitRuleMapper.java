package com.lantu.connect.sysconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.sysconfig.entity.RateLimitRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 RateLimitRule 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface RateLimitRuleMapper extends BaseMapper<RateLimitRule> {
}
