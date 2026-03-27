package com.lantu.connect.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.lantu.connect.auth.entity.PlatformRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 认证 PlatformRole 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface PlatformRoleMapper extends BaseMapper<PlatformRole> {

    @Select("SELECT r.* FROM t_platform_role r INNER JOIN t_user_role_rel ur ON r.id = ur.role_id WHERE ur.user_id = #{userId} ORDER BY r.id ASC")
    @Results(id = "platformRoleMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "role_code", property = "roleCode"),
            @Result(column = "role_name", property = "roleName"),
            @Result(column = "description", property = "description"),
            @Result(column = "permissions", property = "permissions", typeHandler = JacksonTypeHandler.class),
            @Result(column = "is_system", property = "isSystem"),
            @Result(column = "user_count", property = "userCount"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    List<PlatformRole> selectRolesByUserId(@Param("userId") Long userId);
}
