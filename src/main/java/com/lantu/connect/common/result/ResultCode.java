package com.lantu.connect.common.result;

import lombok.Getter;

/**
 * 错误码枚举
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),

    PARAM_ERROR(1001, "参数校验失败"),
    UNAUTHORIZED(1002, "未认证"),
    FORBIDDEN(1003, "权限不足"),
    NOT_FOUND(1004, "资源不存在"),
    CONFLICT(1005, "资源冲突"),
    DUPLICATE_SUBMIT(1006, "重复提交"),
    UNSUPPORTED_FILE_TYPE(1007, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(1008, "文件大小超过限制"),

    /**
     * 已登录但本条网关能力仍须绑定有效 X-Api-Key（如应用启动链接、或非资源发布者调用他人资源）。
     * 与 {@link #UNAUTHORIZED} 区分：避免前端将「缺 Key」误当作登录失效。
     */
    GATEWAY_API_KEY_REQUIRED(1009, "须提供有效的 X-Api-Key"),

    TOKEN_EXPIRED(2001, "Token 已过期"),
    REFRESH_TOKEN_INVALID(2002, "Refresh Token 无效"),
    ACCOUNT_LOCKED(2003, "账户已锁定"),
    PASSWORD_ERROR(2004, "密码错误"),
    SMS_CODE_ERROR(2005, "短信验证码错误或过期"),
    SMS_RATE_LIMITED(2006, "短信发送过于频繁"),
    CSRF_FAILED(2007, "CSRF Token 校验失败"),
    SESSION_MISMATCH(2008, "Session 绑定不匹配"),
    OLD_PASSWORD_ERROR(2009, "旧密码不正确"),
    CAPTCHA_ERROR(2010, "验证码错误或已过期"),

    RATE_LIMITED(3001, "请求过于频繁"),
    DAILY_QUOTA_EXHAUSTED(3002, "日配额已耗尽"),
    MONTHLY_QUOTA_EXHAUSTED(3003, "月配额已耗尽"),
    CIRCUIT_OPEN(3004, "服务熔断中"),
    QUOTA_EXCEEDED(3005, "配额已用尽"),
    /** 与健康检查表联动：down/disabled 时拒绝 invoke，避免用户打到已知不可用资源 */
    RESOURCE_HEALTH_DOWN(3006, "资源健康检查未通过，暂不可调用"),

    ILLEGAL_STATE_TRANSITION(4001, "非法状态流转"),
    REJECT_REASON_REQUIRED(4002, "审核驳回原因不能为空"),
    DUPLICATE_NAME(4003, "同名资源已存在"),
    DUPLICATE_VERSION(4004, "版本号已存在"),
    CANNOT_DELETE_PUBLISHED(4005, "不能删除已发布的资源"),
    DATASET_ACCESS_DENIED(4006, "数据集无访问权限"),
    FAVORITE_EXISTS(4007, "收藏已存在"),
    CANNOT_REVIEW_OWN(4008, "不能评论自己创建的资源"),
    CANNOT_DELETE_SYSTEM_ROLE(4009, "不能删除系统内置角色"),
    GRANT_APPLICATION_DUPLICATE(4010, "已有待审批的授权申请"),
    GRANT_APPLICATION_NOT_FOUND(4011, "授权申请不存在"),
    GRANT_APPLICATION_NOT_PENDING(4012, "授权申请不在待审批状态"),
    GRANT_APPLICATION_NOT_APPROVED(4013, "仅已通过的授权申请可撤回生效授权"),
    GRANT_APPLICATION_NO_ACTIVE_GRANT(4014, "未找到可撤销的生效授权，可能已撤销，请至资源授权管理核对"),

    INTERNAL_ERROR(5001, "内部错误"),
    EXTERNAL_SERVICE_ERROR(5002, "外部服务调用失败"),
    TIMEOUT(5003, "服务超时"),
    FILE_STORAGE_ERROR(5004, "文件存储服务异常"),
    MAIL_SEND_ERROR(5005, "邮件发送失败"),
    SMS_SEND_ERROR(5006, "短信发送失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
