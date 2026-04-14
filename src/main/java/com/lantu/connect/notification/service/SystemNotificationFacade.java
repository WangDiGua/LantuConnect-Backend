package com.lantu.connect.notification.service;

import com.lantu.connect.notification.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified notification facade for workflow/system notifications.
 */
@Service
@RequiredArgsConstructor
public class SystemNotificationFacade {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> PLATFORM_ADMIN_ROLE_CODES = List.of("platform_admin", "admin");
    private static final List<String> AUDIT_AUDIENCE_ROLE_CODES = List.of(
            "platform_admin",
            "admin",
            "reviewer",
            "dept_admin",
            "department_admin",
            "auditor");

    private final MultiChannelNotificationService multiChannelNotificationService;
    private final JdbcTemplate jdbcTemplate;

    public void notifyToUser(Long userId, String type, String title, String body, String sourceType, String sourceId) {
        if (userId == null || userId <= 0L) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);
        dispatch(notification);
    }

    public void notifyToUsers(List<Long> userIds, String type, String title, String body, String sourceType, Long sourceId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String normalizedSourceId = sourceId == null ? null : String.valueOf(sourceId);
        for (Long userId : userIds) {
            notifyToUser(userId, type, title, body, sourceType, normalizedSourceId);
        }
    }

    public void notifyPlatformAdmins(String type, String title, String body, String sourceType, Long sourceId, Long excludeUserId) {
        List<Long> ids = filterExcluded(findPlatformAdminUserIds(), excludeUserId);
        notifyToUsers(ids, type, title, body, sourceType, sourceId);
    }

    public void notifyAuditAudience(
            String type,
            String title,
            String body,
            String sourceType,
            Long sourceId,
            Long excludeUserId,
            String actionUrl,
            String actionLabel) {
        String normalizedSourceId = sourceId == null ? null : String.valueOf(sourceId);
        for (Long userId : filterExcluded(findAuditAudienceUserIds(), excludeUserId)) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setTitle(title);
            notification.setBody(body);
            notification.setSourceType(sourceType);
            notification.setSourceId(normalizedSourceId);
            notification.setActionUrl(actionUrl);
            notification.setActionLabel(actionLabel);
            dispatch(notification);
        }
    }

    public void notifyResourceSubmitted(
            Long submitterId,
            Long resourceId,
            String resourceType,
            String displayName,
            boolean publishedUpdate) {
        String normalizedType = normalizeResourceType(resourceType);
        String title = publishedUpdate ? "已发布资源变更已提交审核" : "资源已提交审核";
        String submitterBody = buildBody(
                publishedUpdate ? "已发布资源变更提审" : "资源提审",
                "待审核",
                String.format(
                        Locale.ROOT,
                        "资源: %s/%s%n名称: %s",
                        fallbackText(normalizedType, "-"),
                        safeId(resourceId),
                        fallbackText(displayName, "-")),
                publishedUpdate
                        ? "审核通过后将合并到线上默认版本。"
                        : "可在资源中心查看审核进度。");
        Notification submitterNotification = new Notification();
        submitterNotification.setUserId(submitterId);
        submitterNotification.setType(NotificationEventCodes.RESOURCE_SUBMITTED);
        submitterNotification.setTitle(title);
        submitterNotification.setBody(submitterBody);
        submitterNotification.setSourceType(normalizedType);
        submitterNotification.setSourceId(resourceId == null ? null : String.valueOf(resourceId));
        submitterNotification.setActionUrl(buildResourceActionUrl(normalizedType, resourceId));
        submitterNotification.setActionLabel("查看资源");
        dispatch(submitterNotification);

        String auditBody = buildBody(
                publishedUpdate ? "已发布资源变更提审" : "资源提审",
                "待处理",
                String.format(
                        Locale.ROOT,
                        "提交人: %s%n资源: %s/%s%n名称: %s",
                        safeId(submitterId),
                        fallbackText(normalizedType, "-"),
                        safeId(resourceId),
                        fallbackText(displayName, "-")),
                "请前往审核列表处理。");
        notifyAuditAudience(
                NotificationEventCodes.RESOURCE_SUBMITTED,
                publishedUpdate ? "已发布资源变更待审核" : "新资源待审核",
                auditBody,
                normalizedType,
                resourceId,
                null,
                "/c/resource-audit",
                "处理审核");
    }

    public void notifyOnboardingSubmitted(Long applicantId, Long applicationId, String companyName, String reason) {
        String applicantDetails = String.format(
                Locale.ROOT,
                "申请人: %s%n公司: %s%n申请理由: %s",
                safeId(applicantId),
                fallbackText(companyName, "-"),
                fallbackText(reason, "-"));
        Notification applicantNotification = new Notification();
        applicantNotification.setUserId(applicantId);
        applicantNotification.setType(NotificationEventCodes.ONBOARDING_SUBMITTED);
        applicantNotification.setTitle("入驻申请已提交");
        applicantNotification.setBody(buildBody("开发者入驻", "待审核", applicantDetails, "可在入驻申请页查看处理进度。"));
        applicantNotification.setSourceType("developer_application");
        applicantNotification.setSourceId(applicationId == null ? null : String.valueOf(applicationId));
        applicantNotification.setActionUrl("/c/developer-onboarding");
        applicantNotification.setActionLabel("查看我的申请");
        dispatch(applicantNotification);

        String reviewerDetails = String.format(
                Locale.ROOT,
                "申请人: %s%n公司: %s%n申请理由: %s",
                safeId(applicantId),
                fallbackText(companyName, "-"),
                fallbackText(reason, "-"));
        notifyAuditAudience(
                NotificationEventCodes.ONBOARDING_SUBMITTED,
                "入驻申请待审核",
                buildBody("开发者入驻", "待处理", reviewerDetails, "请前往入驻审批列表处理。"),
                "developer_application",
                applicationId,
                null,
                "/c/developer-applications",
                "处理入驻");
    }

    public void notifyOnboardingReviewed(Long applicantId, Long applicationId, boolean approved, Long reviewerId, String comment) {
        String type = approved ? NotificationEventCodes.ONBOARDING_APPROVED : NotificationEventCodes.ONBOARDING_REJECTED;
        String title = approved ? "入驻申请已通过" : "入驻申请已驳回";
        String result = approved ? "审核通过" : "审核驳回";
        String details = String.format(
                Locale.ROOT,
                "审核人: %s%n审核意见: %s",
                safeId(reviewerId),
                fallbackText(comment, approved ? "审核通过" : "-"));
        Notification notification = new Notification();
        notification.setUserId(applicantId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(buildBody("开发者入驻", result, details, "可在入驻申请页查看详情。"));
        notification.setSourceType("developer_application");
        notification.setSourceId(applicationId == null ? null : String.valueOf(applicationId));
        notification.setActionUrl("/c/developer-onboarding");
        notification.setActionLabel("查看我的申请");
        dispatch(notification);
    }

    public void notifyPasswordChanged(Long userId) {
        notifyToUser(userId,
                NotificationEventCodes.PASSWORD_CHANGED,
                "账号密码已修改",
                buildBody("账号安全", "成功", "操作: 修改登录密码", "若非本人操作，请立即联系管理员。"),
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
        String details = String.format(Locale.ROOT, "Key ID: %s%nKey 名称: %s", fallbackText(keyId, "-"), fallbackText(keyName, "-"));
        notifyToUser(userId, type, title, buildBody("API Key 管理", result, details, "请妥善保管密钥并定期轮换。"),
                "api_key", keyId);
    }

    public void notifyApiKeyRotated(Long userId, String keyId, String keyName) {
        String details = String.format(Locale.ROOT, "Key ID: %s%nKey 名称: %s", fallbackText(keyId, "-"), fallbackText(keyName, "-"));
        notifyToUser(userId,
                NotificationEventCodes.API_KEY_ROTATED,
                "API Key 已轮换",
                buildBody("API Key 管理", "轮换成功", details, "旧密钥已失效，请立即更新所有集成与环境变量中的 X-Api-Key。"),
                "api_key",
                keyId);
    }

    public void notifyUserStatusChanged(Long targetUserId, Long operatorUserId, String oldStatus, String newStatus) {
        String details = String.format(
                Locale.ROOT,
                "操作人: %s%n状态变更: %s -> %s",
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
                "操作人: %s%n账号: %s",
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
                "操作人: %s%n角色: %s (%s)",
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
                "操作人: %s%n资源: %s/%s%nAPI Key: %s",
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
                "操作人: %s%n资源: %s/%s%n详情: %s",
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
                "操作人: %s%n操作: %s%n配置项: %s",
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
                "规则: %s%n级别: %s%n指标: %s%n阈值: %s%n样本值: %s",
                fallbackText(ruleName, "-"),
                fallbackText(severity, "-"),
                fallbackText(metric, "-"),
                fallbackText(threshold, "-"),
                fallbackText(sample, "-"));
        notifyPlatformAdmins(
                NotificationEventCodes.ALERT_TRIGGERED,
                "系统告警触发: " + fallbackText(ruleName, "-"),
                buildBody("监控告警", "触发", details, "请尽快检查监控面板并处理故障。"),
                "alert",
                parseLongOrNull(recordId),
                null);
    }

    public List<Long> findRoleUserIds(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ur.user_id FROM t_user_role_rel ur JOIN t_platform_role r ON ur.role_id = r.id WHERE r.role_code = ?",
                roleCode.trim().toLowerCase(Locale.ROOT));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(item -> item.get("user_id"))
                .filter(v -> v != null)
                .map(v -> Long.valueOf(String.valueOf(v)))
                .distinct()
                .toList();
    }

    private void dispatch(Notification notification) {
        if (notification == null || notification.getUserId() == null || notification.getUserId() <= 0L) {
            return;
        }
        enrichFlowFields(notification);
        multiChannelNotificationService.sendAll(notification);
    }

    private List<Long> findPlatformAdminUserIds() {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        PLATFORM_ADMIN_ROLE_CODES.forEach(code -> userIds.addAll(findRoleUserIds(code)));
        return List.copyOf(userIds);
    }

    private List<Long> findAuditAudienceUserIds() {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        AUDIT_AUDIENCE_ROLE_CODES.forEach(code -> userIds.addAll(findRoleUserIds(code)));
        return List.copyOf(userIds);
    }

    private static List<Long> filterExcluded(List<Long> userIds, Long excludeUserId) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (excludeUserId == null) {
            return userIds.stream().filter(id -> id != null && id > 0L).distinct().toList();
        }
        return userIds.stream()
                .filter(id -> id != null && id > 0L)
                .filter(id -> !excludeUserId.equals(id))
                .distinct()
                .toList();
    }

    private static String buildResourceActionUrl(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return "/c/resource-center";
        }
        return switch (normalizeResourceType(resourceType)) {
            case "skill" -> "/c/skills-center/" + resourceId;
            case "mcp" -> "/c/mcp-center/" + resourceId;
            case "dataset" -> "/c/dataset-center/" + resourceId;
            case "app" -> "/c/apps-center/" + resourceId;
            default -> "/c/agents-center/" + resourceId;
        };
    }

    private static String normalizeResourceType(String resourceType) {
        return resourceType == null ? "agent" : resourceType.trim().toLowerCase(Locale.ROOT);
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

    private static Long parseLongOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void enrichFlowFields(Notification n) {
        String type = n.getType() == null ? "" : n.getType().trim().toLowerCase(Locale.ROOT);
        n.setCategory(defaultCategory(type));
        n.setSeverity(defaultSeverity(type));

        if (isResourcePublicationEvent(type) && StringUtils.hasText(n.getSourceId())) {
            n.setCategory("workflow");
            n.setAggregateKey("resource:" + n.getSourceId().trim() + ":publication");
            n.setTotalSteps(4);
            if (!StringUtils.hasText(n.getActionLabel())) {
                n.setActionLabel(NotificationEventCodes.RESOURCE_SUBMITTED.equals(type) ? "处理审核" : "查看资源");
            }
            n.setFlowStatus("running");
            if (NotificationEventCodes.RESOURCE_SUBMITTED.equals(type)) {
                setStep(n, 1, "submitted", "提交审核", "done", "资源已进入审核队列");
            } else if (NotificationEventCodes.AUDIT_APPROVED.equals(type)) {
                setStep(n, 2, "reviewed", "审核通过", "done", "资源已通过审核，等待测试灰度或发布上线");
                n.setSeverity("success");
            } else if (NotificationEventCodes.AUDIT_REJECTED.equals(type)) {
                setStep(n, 2, "rejected", "审核驳回", "failed", "资源审核未通过，请查看原因后重新提交");
                n.setFlowStatus("failed");
                n.setSeverity("warning");
            } else if (NotificationEventCodes.RESOURCE_PUBLISHED.equals(type)) {
                setStep(n, 4, "published", "发布上线", "done", "资源已进入平台可用资源池");
                n.setFlowStatus("success");
                n.setSeverity("success");
            }
            return;
        }

        if (isResourceGovernanceEvent(type) && StringUtils.hasText(n.getSourceId())) {
            n.setCategory("workflow");
            n.setAggregateKey("resource:" + n.getSourceId().trim() + ":governance");
            n.setTotalSteps(1);
            n.setCurrentStep(1);
            if (!StringUtils.hasText(n.getActionLabel())) {
                n.setActionLabel("查看资源");
            }
            if (NotificationEventCodes.RESOURCE_VERSION_SWITCHED.equals(type)) {
                setStep(n, 1, "version_switched", "默认版本切换", "done", "资源默认版本已更新");
                n.setFlowStatus("success");
                n.setSeverity("info");
            } else if (NotificationEventCodes.RESOURCE_WITHDRAWN.equals(type)) {
                setStep(n, 1, "withdrawn", "撤回草稿", "done", "资源已撤回到草稿状态");
                n.setFlowStatus("success");
                n.setSeverity("info");
            } else {
                setStep(n, 1, "deprecated", "暂停开放", "warning", "资源已下架或暂停对外开放");
                n.setFlowStatus("warning");
                n.setSeverity("warning");
            }
            return;
        }

        if (type.startsWith("onboarding_") && StringUtils.hasText(n.getSourceId())) {
            n.setCategory("workflow");
            n.setAggregateKey("developer_application:" + n.getSourceId().trim() + ":onboarding");
            n.setTotalSteps(2);
            if (!StringUtils.hasText(n.getActionLabel())) {
                n.setActionLabel(NotificationEventCodes.ONBOARDING_SUBMITTED.equals(type) ? "处理入驻" : "查看入驻申请");
            }
            if (NotificationEventCodes.ONBOARDING_SUBMITTED.equals(type)) {
                n.setFlowStatus("running");
                setStep(n, 1, "submitted", "提交入驻申请", "done", "申请已进入平台审核队列");
            } else if (NotificationEventCodes.ONBOARDING_APPROVED.equals(type)) {
                n.setFlowStatus("success");
                n.setSeverity("success");
                setStep(n, 2, "reviewed", "入驻通过", "done", "开发者入驻申请已通过");
            } else if (NotificationEventCodes.ONBOARDING_REJECTED.equals(type)) {
                n.setFlowStatus("failed");
                n.setSeverity("warning");
                setStep(n, 2, "reviewed", "入驻驳回", "failed", "开发者入驻申请未通过");
            }
            return;
        }

        if (type.startsWith("api_key_") && StringUtils.hasText(n.getSourceId())) {
            n.setCategory("security");
            n.setAggregateKey("api_key:" + n.getSourceId().trim() + ":lifecycle");
            n.setTotalSteps(3);
            if (!StringUtils.hasText(n.getActionLabel())) {
                n.setActionLabel("查看 API Key");
            }
            if (NotificationEventCodes.API_KEY_CREATED.equals(type)) {
                n.setFlowStatus("running");
                setStep(n, 1, "created", "创建密钥", "done", "API Key 已创建");
            } else if (NotificationEventCodes.API_KEY_ROTATED.equals(type)) {
                n.setFlowStatus("running");
                n.setSeverity("warning");
                setStep(n, 2, "rotated", "轮换密钥", "warning", "旧密钥已失效，请更新集成配置");
            } else if (NotificationEventCodes.API_KEY_REVOKED.equals(type)) {
                n.setFlowStatus("success");
                n.setSeverity("warning");
                setStep(n, 3, "revoked", "撤销密钥", "done", "API Key 已撤销");
            }
        }
    }

    private static boolean isResourcePublicationEvent(String type) {
        return NotificationEventCodes.RESOURCE_SUBMITTED.equals(type)
                || NotificationEventCodes.AUDIT_APPROVED.equals(type)
                || NotificationEventCodes.AUDIT_REJECTED.equals(type)
                || NotificationEventCodes.RESOURCE_PUBLISHED.equals(type);
    }

    private static boolean isResourceGovernanceEvent(String type) {
        return NotificationEventCodes.RESOURCE_DEPRECATED.equals(type)
                || NotificationEventCodes.RESOURCE_WITHDRAWN.equals(type)
                || NotificationEventCodes.RESOURCE_VERSION_SWITCHED.equals(type)
                || NotificationEventCodes.PLATFORM_RESOURCE_FORCE_DEPRECATED.equals(type);
    }

    private static void setStep(Notification n, int currentStep, String key, String title, String status, String summary) {
        n.setCurrentStep(currentStep);
        n.setStepKey(key);
        n.setStepTitle(title);
        n.setStepStatus(status);
        n.setStepSummary(summary);
    }

    private static String defaultCategory(String type) {
        if (type.contains("password") || type.contains("session") || type.contains("security")
                || type.contains("api_key") || type.contains("revoked") || type.contains("alert")) {
            return "alert";
        }
        if (type.startsWith("system_")) {
            return "system";
        }
        return "notice";
    }

    private static String defaultSeverity(String type) {
        if (type.contains("rejected") || type.contains("revoked") || type.contains("deprecated")
                || type.contains("killed") || type.contains("alert") || type.contains("security")
                || type.contains("system_param")) {
            return "warning";
        }
        if (type.contains("approved") || type.contains("published") || type.contains("created")
                || type.contains("rotated")) {
            return "success";
        }
        return "info";
    }
}
