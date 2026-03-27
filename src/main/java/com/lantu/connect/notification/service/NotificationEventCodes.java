package com.lantu.connect.notification.service;

/**
 * 统一通知事件编码，供前后端按类型聚合和跳转。
 */
public final class NotificationEventCodes {

    private NotificationEventCodes() {
    }

    public static final String RESOURCE_SUBMITTED = "resource_submitted";
    public static final String RESOURCE_DEPRECATED = "resource_deprecated";
    public static final String RESOURCE_WITHDRAWN = "resource_withdrawn";
    public static final String RESOURCE_VERSION_SWITCHED = "resource_version_switched";

    public static final String GRANT_APPLICATION_NEW = "grant_application_new";
    public static final String GRANT_APPROVED = "grant_approved";
    public static final String GRANT_REJECTED = "grant_rejected";
    public static final String RESOURCE_GRANT_UPDATED = "resource_grant_updated";
    public static final String RESOURCE_GRANT_REVOKED = "resource_grant_revoked";

    public static final String AUDIT_APPROVED = "audit_approved";
    public static final String AUDIT_REJECTED = "audit_rejected";
    public static final String RESOURCE_PUBLISHED = "resource_published";

    public static final String ONBOARDING_SUBMITTED = "onboarding_submitted";
    public static final String ONBOARDING_APPROVED = "onboarding_approved";
    public static final String ONBOARDING_REJECTED = "onboarding_rejected";

    public static final String PASSWORD_CHANGED = "password_changed";
    public static final String PHONE_BOUND = "phone_bound";
    public static final String SESSION_KILLED = "session_killed";

    public static final String API_KEY_CREATED = "api_key_created";
    public static final String API_KEY_REVOKED = "api_key_revoked";

    public static final String USER_STATUS_CHANGED = "user_status_changed";
    public static final String USER_DELETED = "user_deleted";
    public static final String ROLE_CHANGED = "role_changed";

    public static final String SYSTEM_PARAM_CHANGED = "system_param_changed";
    public static final String SECURITY_SETTING_CHANGED = "security_setting_changed";
    public static final String SYSTEM_NETWORK_APPLIED = "system_network_applied";
    public static final String SYSTEM_ACL_PUBLISHED = "system_acl_published";

    public static final String ALERT_TRIGGERED = "alert";
}
