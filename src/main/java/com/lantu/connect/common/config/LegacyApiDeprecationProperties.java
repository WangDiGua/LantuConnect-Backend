package com.lantu.connect.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "lantu.api-deprecation")
public class LegacyApiDeprecationProperties {

    /**
     * 是否启用旧接口废弃策略。
     */
    private boolean enabled = true;

    /**
     * 需要加废弃响应头和日志的路径模式。
     */
    private List<String> deprecatedPatterns = new ArrayList<>(List.of(
            "/v1/**",
            "/agents/**"
    ));

    /**
     * 需要直接禁写（410）的路径模式。
     */
    private List<String> writeBlockedPatterns = new ArrayList<>(List.of(
            "/v1/**",
            "/agents/**"
    ));
}
