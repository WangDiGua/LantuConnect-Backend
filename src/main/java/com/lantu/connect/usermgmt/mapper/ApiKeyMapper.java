package com.lantu.connect.usermgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.usermgmt.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户管理 ApiKey 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {
}
