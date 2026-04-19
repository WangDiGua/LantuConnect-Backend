package com.lantu.connect.gateway.service.support;

import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 统一资源生命周期状态机。
 */
public final class ResourceLifecycleStateMachine {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PENDING_REVIEW = "pending_review";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_DEPRECATED = "deprecated";

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            STATUS_DRAFT, Set.of(STATUS_PENDING_REVIEW, STATUS_REJECTED, STATUS_DEPRECATED),
            STATUS_REJECTED, Set.of(STATUS_DRAFT, STATUS_PENDING_REVIEW, STATUS_DEPRECATED),
            STATUS_PENDING_REVIEW, Set.of(STATUS_PUBLISHED, STATUS_REJECTED, STATUS_DRAFT),
            STATUS_PUBLISHED, Set.of(STATUS_DEPRECATED),
            STATUS_DEPRECATED, Set.of(STATUS_DRAFT, STATUS_PENDING_REVIEW)
    );

    private ResourceLifecycleStateMachine() {
    }

    public static String normalizeStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return STATUS_DRAFT;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static void ensureTransitionAllowed(String from, String to) {
        String src = normalizeStatus(from);
        String dest = normalizeStatus(to);
        if (src.equals(dest)) {
            return;
        }
        Set<String> allowed = TRANSITIONS.get(src);
        if (allowed == null || !allowed.contains(dest)) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION,
                    "资源状态不允许从 " + src + " 流转到 " + dest);
        }
    }

    public static void ensureEditable(String status) {
        String s = normalizeStatus(status);
        if (STATUS_PENDING_REVIEW.equals(s) || STATUS_PUBLISHED.equals(s)) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "当前状态不允许直接修改，请先下线或驳回");
        }
    }

    public static void ensureDeletable(String status) {
        String s = normalizeStatus(status);
        if (STATUS_PUBLISHED.equals(s)) {
            throw new BusinessException(ResultCode.CANNOT_DELETE_PUBLISHED);
        }
        if (STATUS_PENDING_REVIEW.equals(s)) {
            throw new BusinessException(ResultCode.ILLEGAL_STATE_TRANSITION, "审核流程中的资源不可删除");
        }
    }
}

