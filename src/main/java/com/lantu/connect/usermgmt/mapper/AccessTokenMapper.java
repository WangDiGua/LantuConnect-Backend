package com.lantu.connect.usermgmt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.usermgmt.entity.AccessToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户管理 AccessToken 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface AccessTokenMapper extends BaseMapper<AccessToken> {
}
