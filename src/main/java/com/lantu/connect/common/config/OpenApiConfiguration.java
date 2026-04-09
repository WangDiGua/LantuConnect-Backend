package com.lantu.connect.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc 全局 OpenAPI：不强制全接口鉴权（resolve/invoke 等在方法级声明须 {@code X-Api-Key}）。
 */
@Configuration
public class OpenApiConfiguration {

    public static final String API_KEY_SECURITY = "apiKey";
    public static final String BEARER_SECURITY = "bearerAuth";

    @Bean
    public OpenAPI lantuConnectOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("NexusAI Connect API")
                        .version("1.0.0-SNAPSHOT")
                        .description("""
                                兰智通网关、资源目录与相关读模型的 OpenAPI 契约。
                                部署时常配置 servlet context-path（如 `/regis`），则 REST 与文档 URL 为 `{origin}{context-path}/...`
                                （OpenAPI JSON：`{context-path}/v3/api-docs`，Swagger UI：`{context-path}/swagger-ui.html`）。
                                统一响应包装为 `R`：`code == 0` 表示成功，`data` 为载荷。"""))
                .servers(List.of(new Server()
                        .url("/")
                        .description("相对当前主机（已包含应用配置的 context-path）")))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SECURITY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")
                                .description("API Key 明文（`sk_...`）。须具备所需 scope；目录/解析/调用以 Key、scope、资源 published 等为准。"
                                        + "`access_policy` 为历史字段，不代表 Grant 拦截。`POST /catalog/resolve` 与 `invoke*` 通常须提供有效 Key。"))
                        .addSecuritySchemes(BEARER_SECURITY, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Authorization: Bearer <access_token>，由 JwtAuthenticationFilter 校验。")));
    }
}
