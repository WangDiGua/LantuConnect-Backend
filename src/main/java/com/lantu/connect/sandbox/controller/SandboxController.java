package com.lantu.connect.sandbox.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.gateway.dto.InvokeRequest;
import com.lantu.connect.gateway.dto.InvokeResponse;
import com.lantu.connect.sandbox.dto.SandboxSessionCreateRequest;
import com.lantu.connect.sandbox.dto.SandboxSessionVO;
import com.lantu.connect.sandbox.service.SandboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sandbox")
@RequiredArgsConstructor
@Tag(name = "沙箱测试", description = "沙箱会话创建与隔离调用")
public class SandboxController {

    private final SandboxService sandboxService;

    @Operation(summary = "创建沙箱会话")
    @PostMapping("/sessions")
    @RequireRole({"platform_admin", "admin", "reviewer", "developer"})
    public R<SandboxSessionVO> createSession(@Parameter(description = "当前用户ID")
                                             @RequestHeader("X-User-Id") Long userId,
                                             @Parameter(description = "应用API Key")
                                             @RequestHeader("X-Api-Key") String apiKeyRaw,
                                             @RequestBody(required = false) SandboxSessionCreateRequest request) {
        return R.ok(sandboxService.createSession(userId, apiKeyRaw, request));
    }

    @Operation(summary = "查询我的沙箱会话")
    @GetMapping("/sessions/mine")
    @RequireRole({"platform_admin", "admin", "reviewer", "developer"})
    public R<List<SandboxSessionVO>> mySessions(@Parameter(description = "当前用户ID")
                                                @RequestHeader("X-User-Id") Long userId) {
        return R.ok(sandboxService.mySessions(userId));
    }

    @Operation(summary = "沙箱隔离调用")
    @PostMapping("/invoke")
    public R<InvokeResponse> sandboxInvoke(@Parameter(description = "沙箱会话令牌")
                                           @RequestHeader("X-Sandbox-Token") String sandboxToken,
                                           @Parameter(description = "链路追踪ID，可为空")
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                           @Valid @RequestBody InvokeRequest request,
                                           HttpServletRequest httpRequest) {
        String resolvedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : UUID.randomUUID().toString();
        return R.ok(sandboxService.sandboxInvoke(sandboxToken, resolvedTraceId, httpRequest.getRemoteAddr(), request));
    }
}
