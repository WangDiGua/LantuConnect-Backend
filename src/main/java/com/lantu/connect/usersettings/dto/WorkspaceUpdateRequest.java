package com.lantu.connect.usersettings.dto;

import lombok.Data;

import java.util.Map;

/**
 * 用户设置 WorkspaceUpdateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class WorkspaceUpdateRequest {

    private String theme;

    private String locale;

    private Map<String, Object> layout;

    private Map<String, Object> preferences;
}
