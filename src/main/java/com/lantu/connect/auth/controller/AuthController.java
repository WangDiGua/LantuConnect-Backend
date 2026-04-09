package com.lantu.connect.auth.controller;

import com.lantu.connect.auth.dto.AccountInsightsVO;
import com.lantu.connect.auth.dto.ChangePasswordRequest;
import com.lantu.connect.auth.dto.LoginRequest;
import com.lantu.connect.auth.dto.LoginResponse;
import com.lantu.connect.auth.dto.ProfileUpdateRequest;
import com.lantu.connect.auth.dto.RefreshTokenRequest;
import com.lantu.connect.auth.dto.RegisterRequest;
import com.lantu.connect.auth.dto.SessionItem;
import com.lantu.connect.auth.dto.TokenResponse;
import com.lantu.connect.auth.dto.UserInfoVO;
import com.lantu.connect.auth.entity.LoginHistory;
import com.lantu.connect.auth.service.AuthService;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.util.JwtUtil;
import com.lantu.connect.common.web.ClientIpResolver;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证 Auth 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/login")
    @RateLimiter(name = "authLogin")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return R.ok(authService.login(request, clientIpResolver.resolve(http)));
    }

    @PostMapping("/register")
    @RateLimiter(name = "authRegister")
    public R<LoginResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return R.ok(authService.register(request, clientIpResolver.resolve(http)));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(resolveBearer(authorization));
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserInfoVO> me(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(authService.me(userId));
    }

    @PostMapping("/refresh")
    @RateLimiter(name = "authRefresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refresh(request));
    }

    @PostMapping("/change-password")
    public R<Void> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return R.ok();
    }

    @PutMapping("/profile")
    public R<Void> profile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ProfileUpdateRequest request) {
        authService.updateProfile(userId, request);
        return R.ok();
    }

    @GetMapping("/login-history")
    public R<PageResult<LoginHistory>> loginHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(authService.loginHistory(userId, page, pageSize));
    }

    /** 个人资料：安全分、本月登录次数、近 7 日成功登录分布等 */
    @GetMapping("/account-insights")
    public R<AccountInsightsVO> accountInsights(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(authService.accountInsights(userId));
    }

    @GetMapping("/sessions")
    public R<List<SessionItem>> listSessions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String currentSessionId = extractSessionId(authorization);
        return R.ok(authService.listSessions(userId, currentSessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> killSession(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable String sessionId) {
        authService.killSession(userId, sessionId);
        return R.ok();
    }

    private String extractSessionId(String authorization) {
        String token = resolveBearer(authorization);
        if (token == null) return null;
        try {
            Claims claims = jwtUtil.parseToken(token);
            return claims.get("sid", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7).trim();
    }
}
