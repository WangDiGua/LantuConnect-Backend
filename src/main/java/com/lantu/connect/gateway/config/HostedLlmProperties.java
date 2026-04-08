package com.lantu.connect.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hosted Skill 调用的 OpenAI 兼容 Chat Completions 端点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lantu.hosted-llm")
public class HostedLlmProperties {

    /**
     * 关闭时 Hosted Skill invoke 将返回错误（开发环境可 false 以便仅测注册流）。
     */
    private boolean enabled = true;

    /**
     * 例如 https://api.openai.com/v1 或兼容网关；末尾勿带 /chat/completions。
     */
    private String baseUrl = "";

    private String apiKey = "";

    private String defaultModel = "gpt-4o-mini";

    private int connectTimeoutSeconds = 30;

    private int readTimeoutSeconds = 120;
}
