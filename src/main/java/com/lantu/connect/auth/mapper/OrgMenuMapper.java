package com.lantu.connect.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lantu.connect.auth.entity.OrgMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 认证 OrgMenu 数据访问层
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Mapper
public interface OrgMenuMapper extends BaseMapper<OrgMenu> {

    @Select("SELECT COUNT(1) FROM t_org_menu WHERE menu_parent_id = #{menuId}")
    long countChildren(@Param("menuId") Long menuId);
}
