package com.lantu.connect.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证 User 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
