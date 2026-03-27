package com.lantu.connect.sysconfig.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统配置 SystemParamUpsertRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class SystemParamUpsertRequest {

    @NotBlank
    private String paramKey;
    private String paramValue;
    private String description;
}
