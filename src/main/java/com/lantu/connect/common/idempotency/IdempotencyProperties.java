package com.lantu.connect.common.idempotency;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lantu.idempotency")
public class IdempotencyProperties {

    /**
     * 是否启用统一幂等过滤
     */
    private boolean enabled = true;

    /**
     * 请求处理中占位 TTL 秒
     */
    private long processingTtlSeconds = 60;

    /**
     * 成功结果保留 TTL 秒
     */
    private long successTtlSeconds = 86400;
}

