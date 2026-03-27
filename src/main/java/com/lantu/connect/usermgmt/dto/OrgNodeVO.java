package com.lantu.connect.usermgmt.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户管理 OrgNodeVO 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class OrgNodeVO {

    private Long menuId;

    private String menuName;

    private Long menuParentId;

    private Integer menuLevel;

    private Integer ifXy;

    private List<OrgNodeVO> children;
}
