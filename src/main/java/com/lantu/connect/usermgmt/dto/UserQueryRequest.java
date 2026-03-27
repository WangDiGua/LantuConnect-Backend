package com.lantu.connect.usermgmt.dto;

import lombok.Data;

/**
 * 用户管理 UserQueryRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class UserQueryRequest {

    private int page = 1;

    private int pageSize = 10;

    private String sortBy;

    private String sortOrder;
}
