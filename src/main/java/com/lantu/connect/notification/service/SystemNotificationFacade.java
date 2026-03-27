package com.lantu.connect.notification.service;

import com.lantu.connect.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一系统通知门面：封装接收人计算、事件编码与消息内容结构。
 */
@Service
@RequiredArgsConstructor
public class SystemNotificationFacade {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;

    public void notifyToUser(Long userId, String type, String title, String body, String sourceType, String sourceId) {
        if (userId == null) {
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setSourceType(sourceType);
        n.setSourceId(sourceId);
        n.setIsRead(false);
        notificationService.send(n);
    }

    public void notifyToUsers(List<Long> userIds, String type, String title, String body, String sourceType, Long sourceId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        notificationService.broadcast(userIds, type, title, body, sourceType, sourceId);
    }

    public void notifyPlatformAdmins(String type, String title, String body, String sourceType, Long sourceId, Long excludeUserId) {
        List<Long> ids = findRoleUserIds("platform_admin");
        if (excludeUserId != null) {
            ids = ids.stream().filter(id -> !excludeUserId.equals(id)).toList();
        }
        notifyToUsers(ids, type, title, body, sourceType, sourceId);
    }

    public void notifyOnboardingSubmitted(Long applicantId, Long applicationId, String companyName, String reason) {
        String details = String.format(
                Locale.ROOT,
                "申请人: %s\n公司: %s\n申请理由: %s",
                safeId(applicantId),
                fallbackText(companyName, "-"),
                fallbackText(reason, "-"));
        notifyPlatformAdmins(
                NotificationEventCodes.ONBOARDING_SUBMITTED,
                "入驻申请待审核",
                buildBody("入驻申请", "待处理", details, "请前往入驻审核列表处理。"),
                "developer_application",
                applicationId,
                null);
    }

    public void notifyOnboardingReviewed(Long applicantId, Long applicationId, boolean approved, Long reviewerId, String comment) {
        String type = approved ? NotificationEventCodes.ONBOARDING_APPROVED : NotificationEventCodes.ONBOARDING_REJECTED;
        String title = approved ? "入驻申请已通过" : "入驻申请被驳回";
        String result = approved ? "审核通过" : "审核驳回";
        String details = String.format(
                Locale.ROOT,
                "审核人: %s\n审批意见: %s",
                safeId(reviewerId),
                fallbackText(comment, approved ? "审核通过" : "-"));
        notifyToUser(applicantId, type, title, buildBody("入驻申请", result, details, "可在入驻申请记录中查看详情。"),
                "developer_application", applicationId == null ? null : String.valueOf(applicationId));
    }

    public void notifyPasswordChanged(Long userId) {
        notifyToUser(userId,
                NotificationEventCodes.PASSWORD_CHANGED,
                "账号密码已修改",
                buildBody("账号安全", "成功", "操作: 修改登录密码", "若非本人操作，请立即联系管理员。"),
                "user",
                userId == null ? null : String.valueOf(userId));
    }

    public void notifyPhoneBound(Long userId, String phone) {
        notifyToUser(userId,
                NotificationEventCodes.PHONE_BOUND,
                "手机号绑定成功",
                buildBody("账号安全", "成功", "绑定手机号: " + fallbackText(phone, "-"), "可在个人设置中继续维护账号安全信息。"),
                "user",
                userId == null ? null : String.valueOf(userId));
    }

    public void notifySessionKilled(Long userId, String sessionId) {
        notifyToUser(userId,
                NotificationEventCodes.SESSION_KILLED,
                "会话已下线",
                buildBody("账号安全", "成功", "被下线会话: " + fallbackText(sessionId, "-"), "如非本人操作，请立即修改密码。"),
                "session",
                sessionId);
    }

    public void notifyApiKeyChanged(Long userId, String keyId, String keyName, boolean created) {
        String type = created ? NotificationEventCodes.API_KEY_CREATED : NotificationEventCodes.API_KEY_REVOKED;
        String title = created ? "API Key 已创建" : "API Key 已撤销";
        String result = created ? "创建成功" : "撤销成功";
        String details = String.format(Locale.ROOT, "Key ID: %s\nKey 名称: %s", fallbackText(keyId, "-"), fallbackText(keyName, "-"));
        notifyToUser(userId, type, title, buildBody("API Key 管理", result, details, "请妥善保管密钥并定期轮换。"),
                "api_key", keyId);
    }

    public void notifyUserStatusChanged(Long targetUserId, Long operatorUserId, String oldStatus, String newStatus) {
        String details = String.format(
                Locale.ROOT,
                "操作人: %s\n状态变更: %s -> %s",
                safeId(operatorUserId),
                fallbackText(oldStatus, "-"),
                fallbackText(newStatus, "-"));
        notifyToUser(targetUserId,
                NotificationEventCodes.USER_STATUS_CHANGED,
                "账号状态已变更",
                buildBody("用户管理", "状态已更新", details, "如有疑问请联系平台管理员。"),
                "user",
                targetUserId == null ? null : String.valueOf(targetUserId));
    }

    public void notifyUserDeleted(Long targetUserId, Long operatorUserId, String username) {
        String details = String.format(
                Locale.ROOT,
                "操作人: %s\n账号: %s",
                safeId(operatorUserId),
                fallbackText(username, "-"));
        notifyPlatformAdmins(
                NotificationEventCodes.USER_DELETED,
                "用户账号已删除",
                buildBody("高危操作", "已执行", details, "请核对是否需要回收关联权限与资源。"),
                "user",
                targetUserId,
                null);
    }

    public void notifyRoleChanged(Long operatorUserId, Long roleId, String roleCode, String operation) {
        String details = String.format(
                Locale.ROOT,
                "操作人: %s\n角色: %s (%s)",
                safeId(operatorUserId),
                fallbackText(roleCode, "-"),
                fallbackText(roleId == null ? null : String.valueOf(roleId), "-"));
        notifyPlatformAdmins(
                NotificationEventCodes.ROLE_CHANGED,
                "角色权限配置变更",
                buildBody("高危操作", fallbackText(operation, "变更"), details, "建议复核关键资源的访问权限。"),
                "role",
                roleId,
                operatorUserId);
    }

    public void notifyResourceGrantChanged(Long operatorUserId, String eventType, String resourceType, Long resourceId, String apiKeyId) {
        String details = String.format(
                Locale.ROOT,
                "操作人: %s\n资源: %s/%s\nAPI Key: %s",
                safeId(operatorUserId),
                fallbackText(resourceType, "-"),
                fallbackText(resourceId == null ? null : String.valueOf(resourceId), "-"),
                fallbackText(apiKeyId, "-"));
        String title = NotificationEventCodes.RESOURCE_GRANT_REVOKED.equals(eventType) ? "资源授权已撤销" : "资源授权已更新";
        notifyPlatformAdmins(
                eventType,
                title,
                buildBody("资源授权", "已生效", details, "可在资源授权列表查看最新状态。"),
                "resource",
                resourceId,
                null);
    }

    public void notifyResourceStateChange(Long operatorUserId, String type, String title, String resourceType, Long resourceId, String extra) {
        String details = String.format(
                Locale.ROOT,
                "操作人: %s\n资源: %s/%s\n详情: %s",
                safeId(operatorUserId),
                fallbackText(resourceType, "-"),
                fallbackText(resourceId == null ? null : String.valueOf(resourceId), "-"),
                fallbackText(extra, "-"));
        notifyPlatformAdmins(
                type,
                title,
                buildBody("资源治理", "已执行", details, "可在资源管理中确认当前状态。"),
                "resource",
                resourceId,
                null);
    }

    public void notifySystemSecurityOperation(Long operatorUserId, String type, String action, String key) {
        String details = String.format(
                Locale.ROOT,
                "操作人: %s\n操作: %s\n配置项: %s",
                safeId(operatorUserId),
                fallbackText(action, "-"),
                fallbackText(key, "-"));
        notifyPlatformAdmins(
                type,
                "系统安全配置变更",
                buildBody("高危操作", "已执行", details, "建议在审计日志中复核本次变更。"),
                "system-config",
                null,
                operatorUserId);
    }

    public void notifyAlertTriggered(String ruleName, String severity, String metric, String threshold, String sample, String recordId) {
        String details = String.format(
                Locale.ROOT,
                "规则: %s\n级别: %s\n指标: %s\n阈值: %s\n样本值: %s",
                fallbackText(ruleName, "-"),
                fallbackText(severity, "-"),
                fallbackText(metric, "-"),
                fallbackText(threshold, "-"),
                fallbackText(sample, "-"));
        notifyToUser(
                0L,
                NotificationEventCodes.ALERT_TRIGGERED,
                "系统告警触发: " + fallbackText(ruleName, "-"),
                buildBody("监控告警", "触发", details, "请尽快检查监控面板并处理故障。"),
                "alert",
                recordId);
    }

    public List<Long> findRoleUserIds(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ur.user_id FROM t_user_role_rel ur JOIN t_platform_role r ON ur.role_id = r.id WHERE r.role_code = ?",
                roleCode.trim().toLowerCase(Locale.ROOT));
        return rows.stream()
                .map(item -> item.get("user_id"))
                .filter(v -> v != null)
                .map(v -> Long.valueOf(String.valueOf(v)))
                .distinct()
                .toList();
    }

    private static String buildBody(String event, String result, String details, String suggestion) {
        return """
                事件: %s
                结果: %s
                时间: %s
                详情:
                %s
                建议: %s
                """.formatted(
                fallbackText(event, "-"),
                fallbackText(result, "-"),
                TS.format(LocalDateTime.now()),
                fallbackText(details, "-"),
                fallbackText(suggestion, "-"));
    }

    private static String safeId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }

    private static String fallbackText(String text, String fallback) {
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }
}
