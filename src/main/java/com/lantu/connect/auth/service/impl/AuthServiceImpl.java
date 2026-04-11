package com.lantu.connect.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lantu.connect.auth.dto.*;
import com.lantu.connect.auth.entity.OrgMenu;
import com.lantu.connect.auth.entity.PlatformRole;
import com.lantu.connect.auth.entity.LoginHistory;
import com.lantu.connect.auth.entity.User;
import com.lantu.connect.auth.mapper.LoginHistoryMapper;
import com.lantu.connect.auth.mapper.OrgMenuMapper;
import com.lantu.connect.auth.mapper.PlatformRoleMapper;
import com.lantu.connect.auth.mapper.UserMapper;
import com.lantu.connect.auth.mapper.UserRoleRelMapper;
import com.lantu.connect.auth.entity.UserRoleRel;
import com.lantu.connect.auth.service.AuthService;
import com.lantu.connect.auth.support.AccessTokenBlacklist;
import com.lantu.connect.auth.support.SessionRevocationRegistry;
import com.lantu.connect.common.captcha.CaptchaService;
import com.lantu.connect.common.geo.GeoIpLookupService;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.security.RedisAuthRateLimiter;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.ResultCode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.session.SessionGeoEnrichmentService;
import com.lantu.connect.common.session.SessionTrackerService;
import com.lantu.connect.common.util.JwtUtil;
import com.lantu.connect.common.web.ClientIpResolver;
import com.lantu.connect.notification.service.SystemNotificationFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 认证服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REFRESH_USED_PREFIX = "token:refresh:";

    /** 同一 refresh 并发刷新时缓存新发 token 对，供另一请求复用（避免 React StrictMode 等双次请求误杀）。 */
    private static final String REFRESH_PAIR_PREFIX = "token:refresh:pair:";
    private final UserMapper userMapper;
    private final UserRoleRelMapper userRoleRelMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final OrgMenuMapper orgMenuMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final SessionRevocationRegistry sessionRevocationRegistry;
    private final LoginHistoryMapper loginHistoryMapper;
    private final SessionTrackerService sessionTrackerService;
    private final SessionGeoEnrichmentService sessionGeoEnrichmentService;
    private final GeoIpLookupService geoIpLookupService;
    private final CaptchaService captchaService;
    private final TransactionTemplate transactionTemplate;
    private final SystemNotificationFacade systemNotificationFacade;
    private final RedisAuthRateLimiter redisAuthRateLimiter;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final CasbinAuthorizationService casbinAuthorizationService;

    /** JWT /me 主角色：多角色时取职级最高者（与 {@code ORDER BY r.id} 无关）。 */
    private static final List<String> PLATFORM_ROLE_PRECEDENCE = List.of(
            "platform_admin", "admin", "reviewer", "developer", "user");

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        redisAuthRateLimiter.checkLogin(clientIp, request.getUsername());
        if (StringUtils.hasText(request.getCaptchaId()) && StringUtils.hasText(request.getCaptchaCode())) {
            if (!captchaService.verify(request.getCaptchaId(), request.getCaptchaCode())) {
                throw new BusinessException(ResultCode.CAPTCHA_ERROR);
            }
        } else {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR);
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            writeLoginHistory(null, request.getUsername(), "password", "fail", "用户不存在", true);
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "用户名或密码错误");
        }
        if ("locked".equals(user.getStatus())) {
            writeLoginHistory(user.getUserId(), user.getUsername(), "password", "locked", "账户已锁定", true);
            throw new BusinessException(ResultCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            writeLoginHistory(user.getUserId(), user.getUsername(), "password", "fail", "密码错误", true);
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "用户名或密码错误");
        }

        user.setLastLoginTime(LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> {
            userMapper.updateById(user);
            writeLoginHistory(user.getUserId(), user.getUsername(), "password", "success", null, false);
        });
        return buildLoginResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request, String clientIp) {
        redisAuthRateLimiter.checkRegister(clientIp);
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "两次密码输入不一致");
        }
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getUsername());
        user.setMail(request.getEmail());
        user.setMobile(request.getPhone());
        user.setSex(0);
        user.setSchoolId(1L);
        /** 自助注册默认 {@code user}：使用已上架五类资源、个人 API Key、resolve/invoke 等消费路径；无 per-resource Grant 主路径；可申请开发者入驻；无资源注册/发布/平台级审核。 */
        user.setRole(1);
        user.setStatus("active");
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.insert(user);

        PlatformRole endUserRole = platformRoleMapper.selectOne(
                new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, "user"));
        if (endUserRole == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "系统未配置 user 平台角色，无法完成注册");
        }
        UserRoleRel rel = new UserRoleRel();
        rel.setUserId(user.getUserId());
        rel.setRoleId(endUserRole.getId());
        userRoleRelMapper.insert(rel);

        return buildLoginResponse(user);
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少 Authorization");
        }
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少有效 Token");
        }
        accessTokenBlacklist.add(token);
        try {
            Claims claims = jwtUtil.parseToken(token);
            String sessionId = claims.get("sid", String.class);
            if (sessionId != null) {
                sessionRevocationRegistry.revoke(sessionId);
                sessionTrackerService.removeSession(sessionId);
            }
        } catch (JwtException e) {
            log.debug("登出时解析token失败，忽略会话清理: {}", e.getMessage());
        }
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        String tokenHash = sha256(refreshToken);
        String usedKey = REFRESH_USED_PREFIX + tokenHash;
        String pairKey = REFRESH_PAIR_PREFIX + tokenHash;

        String cachedPair = redisTemplate.opsForValue().get(pairKey);
        if (StringUtils.hasText(cachedPair)) {
            TokenResponse fromCache = parseCachedRefreshPair(cachedPair);
            if (fromCache != null) {
                return fromCache;
            }
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID, "Refresh Token 已过期");
        } catch (JwtException e) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        User user = userMapper.selectById(userId);
        if (user == null || !"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不可用");
        }

        String roleCode = resolvePrimaryRoleCode(userId);
        String sid = claims.get("sid", String.class);
        boolean isNewSession = !StringUtils.hasText(sid);
        if (isNewSession) {
            sid = java.util.UUID.randomUUID().toString();
        }

        if (isNewSession) {
            String clientIp = resolveClientIp();
            sessionTrackerService.trackSessionWithMeta(userId, sid, clientIp, resolveUserAgent());
            sessionGeoEnrichmentService.enqueueLocationLookup(sid, clientIp);
        }

        boolean markedUsed = false;
        try {
            Boolean firstUse = redisTemplate.opsForValue().setIfAbsent(usedKey, "1", Duration.ofDays(7));
            if (!Boolean.TRUE.equals(firstUse)) {
                TokenResponse waited = waitForConcurrentRefreshPair(pairKey);
                if (waited != null) {
                    return waited;
                }
                throw new BusinessException(ResultCode.REFRESH_TOKEN_INVALID, "Refresh Token 已被使用");
            }
            markedUsed = true;

            String newAccessToken = jwtUtil.generateAccessToken(userId, username, Map.of("role", roleCode, "sid", sid));
            String newRefreshToken = jwtUtil.generateRefreshToken(userId, username);
            TokenResponse out = TokenResponse.builder()
                    .token(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build();
            try {
                redisTemplate.opsForValue().set(pairKey, objectMapper.writeValueAsString(out), Duration.ofSeconds(120));
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "Token 序列化失败");
            }
            return out;
        } catch (BusinessException e) {
            if (markedUsed) {
                redisTemplate.delete(usedKey);
            }
            throw e;
        } catch (RuntimeException e) {
            if (markedUsed) {
                redisTemplate.delete(usedKey);
            }
            throw e;
        }
    }

    private TokenResponse parseCachedRefreshPair(String json) {
        try {
            return objectMapper.readValue(json, TokenResponse.class);
        } catch (JsonProcessingException e) {
            log.debug("refresh pair cache parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 另一请求已占用 refresh 单次消费位但可能正在写入 pair；短暂等待，避免并发 refresh 导致误退出。
     */
    private TokenResponse waitForConcurrentRefreshPair(String pairKey) {
        for (int i = 0; i < 15; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String cached = redisTemplate.opsForValue().get(pairKey);
            if (StringUtils.hasText(cached)) {
                TokenResponse parsed = parseCachedRefreshPair(cached);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    @Override
    public UserInfoVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toUserInfoVO(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
        sessionTrackerService.removeAllUserSessions(userId);
        systemNotificationFacade.notifyPasswordChanged(userId);
    }

    @Override
    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (request.getAvatar() != null) {
            user.setHeadImage(request.getAvatar());
        }
        if (request.getLanguage() != null) {
            user.setLanguage(request.getLanguage());
        }
        userMapper.updateById(user);
    }

    @Override
    public List<SessionItem> listSessions(Long userId, String currentSessionId) {
        Set<String> sessionIds = sessionTrackerService.getActiveSessions(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        List<SessionItem> items = new java.util.ArrayList<>();
        List<String> staleIds = new java.util.ArrayList<>();
        for (String sid : sessionIds) {
            java.util.Map<String, String> meta = sessionTrackerService.getSessionMeta(sid);
            if (meta.isEmpty()) {
                staleIds.add(sid);
                continue;
            }
            items.add(SessionItem.builder()
                    .id(sid)
                    .device(meta.getOrDefault("device", "Unknown"))
                    .os(meta.getOrDefault("os", "Unknown"))
                    .browser(meta.getOrDefault("browser", "Unknown"))
                    .ip(meta.getOrDefault("ip", ""))
                    .location(meta.get("location"))
                    .loginAt(meta.get("loginAt"))
                    .lastActiveAt(meta.get("lastActiveAt"))
                    .current(sid.equals(currentSessionId))
                    .build());
        }
        for (String staleId : staleIds) {
            sessionTrackerService.removeSession(staleId);
        }
        return items;
    }

    @Override
    public void killSession(Long userId, String sessionId) {
        Set<String> sessionIds = sessionTrackerService.getActiveSessions(userId);
        if (sessionIds == null || !sessionIds.contains(sessionId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        sessionRevocationRegistry.revoke(sessionId);
        sessionTrackerService.removeSession(sessionId);
        systemNotificationFacade.notifySessionKilled(userId, sessionId);
    }

    private LoginResponse buildLoginResponse(User user) {
        String roleCode = resolvePrimaryRoleCode(user.getUserId());

        String sessionId = java.util.UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(
                user.getUserId(), user.getUsername(), Map.of("role", roleCode, "sid", sessionId));
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserId(), user.getUsername());

        String clientIp = resolveClientIp();
        sessionTrackerService.trackSessionWithMeta(
                user.getUserId(), sessionId, clientIp, resolveUserAgent());
        sessionGeoEnrichmentService.enqueueLocationLookup(sessionId, clientIp);

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .user(toUserInfoVO(user))
                .expiresIn(accessTokenExpiry)
                .build();
    }

    private UserInfoVO toUserInfoVO(User user) {
        String roleCode = resolvePrimaryRoleCode(user.getUserId());

        String department = null;
        if (user.getMenuId() != null) {
            OrgMenu menu = orgMenuMapper.selectById(user.getMenuId());
            if (menu != null) {
                department = menu.getMenuName();
            }
        }

        UserInfoVO.UserInfoVOBuilder b = UserInfoVO.builder()
                .id(String.valueOf(user.getUserId()))
                .username(user.getRealName())
                .email(user.getMail())
                .phone(user.getMobile())
                .avatar(user.getHeadImage())
                .nickname(user.getRealName())
                .role(roleCode)
                .status(user.getStatus())
                .department(department)
                .lastLoginAt(user.getLastLoginTime())
                .createdAt(user.getCreateTime())
                .updatedAt(user.getUpdateTime())
                .language(user.getLanguage() != null ? user.getLanguage() : "zh-CN")
                .permissions(casbinAuthorizationService.effectivePermissionStrings(user.getUserId()));
        return b.build();
    }

    private String resolvePrimaryRoleCode(Long userId) {
        List<PlatformRole> roles = platformRoleMapper.selectRolesByUserId(userId);
        if (roles.isEmpty()) {
            PlatformRole endUserRole = platformRoleMapper.selectOne(
                    new LambdaQueryWrapper<PlatformRole>().eq(PlatformRole::getRoleCode, "user"));
            return endUserRole != null ? normalizeRoleCode(endUserRole.getRoleCode()) : "unassigned";
        }
        return roles.stream()
                .map(PlatformRole::getRoleCode)
                .min(Comparator.comparingInt(code -> {
                    String n = normalizeRoleCode(code);
                    int idx = PLATFORM_ROLE_PRECEDENCE.indexOf(n);
                    return idx < 0 ? PLATFORM_ROLE_PRECEDENCE.size() : idx;
                }))
                .map(this::normalizeRoleCode)
                .orElseGet(() -> normalizeRoleCode(roles.get(0).getRoleCode()));
    }

    private String normalizeRoleCode(String raw) {
        if (raw == null) {
            return "";
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        // 兼容迁移前 JWT/库中旧码
        return switch (n) {
            case "consumer" -> "user";
            case "dept_admin" -> "reviewer";
            default -> n;
        };
    }

    @Override
    public PageResult<LoginHistory> loginHistory(Long userId, int page, int pageSize) {
        Page<LoginHistory> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<LoginHistory> q = new LambdaQueryWrapper<LoginHistory>()
                .eq(LoginHistory::getUserId, userId)
                .orderByDesc(LoginHistory::getLoginTime);
        Page<LoginHistory> result = loginHistoryMapper.selectPage(p, q);
        return PageResults.from(result);
    }

    @Override
    public AccountInsightsVO accountInsights(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }

        long totalSuccess = loginHistoryMapper.selectCount(
                new LambdaQueryWrapper<LoginHistory>()
                        .eq(LoginHistory::getUserId, userId)
                        .eq(LoginHistory::getResult, "success"));

        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = today.plusMonths(1).withDayOfMonth(1).atStartOfDay();
        long monthSuccess = loginHistoryMapper.selectCount(
                new LambdaQueryWrapper<LoginHistory>()
                        .eq(LoginHistory::getUserId, userId)
                        .eq(LoginHistory::getResult, "success")
                        .ge(LoginHistory::getLoginTime, monthStart)
                        .lt(LoginHistory::getLoginTime, nextMonthStart));

        List<Integer> byDay = new ArrayList<>(7);
        List<String> labels = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            labels.add(d.toString());
            LocalDateTime dayStart = d.atStartOfDay();
            LocalDateTime dayEnd = d.plusDays(1).atStartOfDay();
            long c = loginHistoryMapper.selectCount(
                    new LambdaQueryWrapper<LoginHistory>()
                            .eq(LoginHistory::getUserId, userId)
                            .eq(LoginHistory::getResult, "success")
                            .ge(LoginHistory::getLoginTime, dayStart)
                            .lt(LoginHistory::getLoginTime, dayEnd));
            byDay.add((int) Math.min(Integer.MAX_VALUE, c));
        }

        long fail30 = loginHistoryMapper.selectCount(
                new LambdaQueryWrapper<LoginHistory>()
                        .eq(LoginHistory::getUserId, userId)
                        .in(LoginHistory::getResult, List.of("fail", "locked"))
                        .ge(LoginHistory::getLoginTime, LocalDateTime.now().minusDays(30)));

        long activeSessions = sessionTrackerService.getActiveSessionCount(userId);

        int score = 100;
        if ("locked".equalsIgnoreCase(String.valueOf(user.getStatus()).trim())) {
            score = 25;
        } else {
            score -= Math.min(45, (int) fail30 * 6);
            if (activeSessions > 5) {
                score -= 12;
            } else if (activeSessions > 3) {
                score -= 6;
            }
            score = Math.max(0, Math.min(100, score));
        }

        String level;
        String label;
        if (score >= 75) {
            level = "good";
            label = "良好";
        } else if (score >= 50) {
            level = "fair";
            label = "一般";
        } else {
            level = "warn";
            label = "注意";
        }

        return AccountInsightsVO.builder()
                .securityScore(score)
                .securityLevel(level)
                .securityLabel(label)
                .totalSuccessLogins(totalSuccess)
                .loginCountThisMonth(monthSuccess)
                .recentSuccessByDay(byDay)
                .recentDayLabels(labels)
                .failedAttemptsLast30Days(fail30)
                .activeSessionCount(activeSessions)
                .build();
    }

    private void writeLoginHistory(Long userId, String username, String loginType,
                                   String result, String failureReason, boolean bestEffort) {
        try {
            LoginHistory h = new LoginHistory();
            h.setUserId(userId);
            h.setUsername(username);
            h.setLoginTime(LocalDateTime.now());
            String ip = resolveClientIp();
            String ua = resolveUserAgent();
            h.setIp(ip);
            h.setUserAgent(ua);
            h.setDevice(parseDeviceFromUa(ua));
            h.setOs(parseOsFromUa(ua));
            h.setBrowser(parseBrowserFromUa(ua));
            h.setLocation(geoIpLookupService.lookup(ip));
            h.setLoginType(loginType);
            h.setResult(result);
            h.setFailureReason(failureReason);
            loginHistoryMapper.insert(h);
        } catch (RuntimeException e) {
            if (bestEffort) {
                log.warn("写入登录历史失败: {}", e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private String resolveClientIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                String ip = clientIpResolver.resolve(sra.getRequest());
                return StringUtils.hasText(ip) ? ip : "0.0.0.0";
            }
        } catch (RuntimeException e) {
            log.debug("resolveClientIp failed: {}", e.toString());
        }
        return "0.0.0.0";
    }

    private static String resolveUserAgent() {
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
                String ua = sra.getRequest().getHeader("User-Agent");
                if (StringUtils.hasText(ua)) {
                    return ua.length() > 512 ? ua.substring(0, 512) : ua;
                }
            }
        } catch (RuntimeException e) {
            // optional header context; avoid noise at INFO
        }
        return null;
    }

    private static String parseDeviceFromUa(String ua) {
        if (ua == null) {
            return "Unknown";
        }
        String lower = ua.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) {
            return "Mobile";
        }
        if (lower.contains("tablet") || lower.contains("ipad")) {
            return "Tablet";
        }
        return "Desktop";
    }

    private static String parseOsFromUa(String ua) {
        if (ua == null) {
            return "Unknown";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS";
        }
        return "Unknown";
    }

    private static String parseBrowserFromUa(String ua) {
        if (ua == null) {
            return "Unknown";
        }
        if (ua.contains("Edg/")) {
            return "Edge";
        }
        if (ua.contains("Chrome/") && !ua.contains("Edg/")) {
            return "Chrome";
        }
        if (ua.contains("Firefox/")) {
            return "Firefox";
        }
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) {
            return "Safari";
        }
        return "Unknown";
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
