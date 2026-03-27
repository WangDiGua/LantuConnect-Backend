package com.lantu.connect.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 监控 HealthConfigUpsertRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class HealthConfigUpsertRequest {

    private Long id;
    @NotBlank
    private String targetName;
    private String targetUrl;
    private Integer checkIntervalSec;
    private Integer enabled;
}
