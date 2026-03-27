package com.lantu.connect.usersettings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 用户设置 WorkspaceSettingsVO 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceSettingsVO {

    private String theme;

    private String locale;

    private Map<String, Object> layout;

    private Map<String, Object> preferences;
}
