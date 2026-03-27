package com.lantu.connect.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.auth.entity.UserRoleRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 认证 UserRoleRel 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface UserRoleRelMapper extends BaseMapper<UserRoleRel> {

    @Select("SELECT * FROM t_user_role_rel WHERE user_id = #{userId} ORDER BY role_id ASC")
    List<UserRoleRel> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT role_id FROM t_user_role_rel WHERE user_id = #{userId} ORDER BY role_id ASC")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
