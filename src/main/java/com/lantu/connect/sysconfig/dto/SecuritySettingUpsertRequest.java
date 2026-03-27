package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统配置 SecuritySettingUpsertRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class SecuritySettingUpsertRequest {

    @NotBlank
    private String settingKey;
    private String settingValue;
}
