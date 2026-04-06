package com.lantu.connect.realtime;

import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import com.lantu.connect.common.config.CorsBootstrapProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 浏览器实时推送端点：{@code /ws/push?access_token=...}。
 * 允许的 Origin 与 HTTP CORS 策略对齐，避免生产误配为 * 时不经意放宽（由配置显式控制）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final UserPushWebSocketHandler userPushWebSocketHandler;
    private final JwtWebSocketHandshakeInterceptor handshakeInterceptor;
    private final RuntimeAppConfigService runtimeAppConfigService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] patterns = resolveAllowedOriginPatterns().toArray(String[]::new);
        registry.addHandler(userPushWebSocketHandler, "/ws/push")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(patterns);
    }

    private List<String> resolveAllowedOriginPatterns() {
        CorsBootstrapProperties c = runtimeAppConfigService.cors();
        if (c.isAllowAllOrigins()) {
            return List.of("*");
        }
        Set<String> patterns = new LinkedHashSet<>();
        if (c.isRelaxLocalhost()) {
            patterns.add("http://localhost:*");
            patterns.add("http://127.0.0.1:*");
            patterns.add("https://localhost:*");
            patterns.add("https://127.0.0.1:*");
        }
        String raw = c.getAllowedOrigins() != null ? c.getAllowedOrigins() : "";
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(patterns::add);
        if (patterns.isEmpty()) {
            patterns.add("http://localhost:*");
            patterns.add("http://127.0.0.1:*");
        }
        return new ArrayList<>(patterns);
    }
}
