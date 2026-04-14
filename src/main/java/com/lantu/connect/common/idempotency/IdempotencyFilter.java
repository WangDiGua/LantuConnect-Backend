package com.lantu.connect.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.sysconfig.runtime.RuntimeAppConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String PREFIX = "idem:req:";

    private final StringRedisTemplate redisTemplate;
    private final RuntimeAppConfigService runtimeAppConfigService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        IdempotencyProperties properties = runtimeAppConfigService.idempotency();
        if (!properties.isEnabled() || !MUTATING.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String idemKey = request.getHeader(HEADER);
        if (!StringUtils.hasText(idemKey)) {
            filterChain.doFilter(request, response);
            return;
        }
        String uid = request.getHeader("X-User-Id");
        String uri = request.getRequestURI();
        String key = PREFIX + (StringUtils.hasText(uid) ? uid.trim() : "anonymous") + ":" + uri + ":" + idemKey.trim();

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                key, "processing", Objects.requireNonNull(Duration.ofSeconds(Math.max(1, properties.getProcessingTtlSeconds()))));
        if (Boolean.FALSE.equals(locked)) {
            writeDuplicate(response);
            return;
        }

        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            if (status >= 200 && status < 300) {
                redisTemplate.opsForValue().set(
                        key, "success", Objects.requireNonNull(Duration.ofSeconds(Math.max(60, properties.getSuccessTtlSeconds()))));
            } else {
                redisTemplate.delete(key);
            }
        }
    }

    private void writeDuplicate(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = objectMapper.writeValueAsString(Map.of(
                "code", ResultCode.DUPLICATE_SUBMIT.getCode(),
                "message", "重复提交，请稍后重试",
                "data", null
        ));
        response.getWriter().write(body);
    }
}

