package com.lantu.connect.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.notification.entity.Notification;
import com.lantu.connect.notification.mapper.NotificationMapper;
import com.lantu.connect.notification.service.NotificationService;
import com.lantu.connect.realtime.RealtimePushService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 通知Notification服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final RealtimePushService realtimePushService;
    private final ObjectMapper objectMapper;

    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void send(Notification notification) {
        if (notification == null || notification.getUserId() == null) {
            return;
        }
        if (StringUtils.hasText(notification.getAggregateKey())) {
            sendAggregated(notification);
            return;
        }
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        if (!StringUtils.hasText(notification.getCategory())) {
            notification.setCategory(resolveLegacyCategory(notification.getType()));
        }
        LocalDateTime now = LocalDateTime.now();
        notification.setLastEventTime(now);
        notification.setUpdateTime(now);
        notificationMapper.insert(notification);
        publishCreated(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void broadcast(List<Long> userIds, String type, String title, String body, String sourceType, Long sourceId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        for (Long uid : userIds) {
            Notification n = new Notification();
            n.setUserId(uid);
            n.setType(type);
            n.setTitle(title);
            n.setBody(body);
            n.setSourceType(sourceType);
            n.setSourceId(sourceId != null ? String.valueOf(sourceId) : null);
            n.setIsRead(false);
            send(n);
        }
    }

    @Override
    public Page<Notification> listByUser(Long userId, int page, int pageSize) {
        return listByUser(userId, page, pageSize, null, null, null, null, null, null, null);
    }

    @Override
    public Page<Notification> listByUser(Long userId, int page, int pageSize, String type, Boolean isRead,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        return listByUser(userId, page, pageSize, type, null, null, null, isRead, startTime, endTime);
    }

    @Override
    public Page<Notification> listByUser(Long userId, int page, int pageSize, String type, String category,
                                         String severity, String flowStatus, Boolean isRead,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        Page<Notification> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> q = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(isRead != null, Notification::getIsRead, isRead)
                .ge(startTime != null, Notification::getCreateTime, startTime)
                .le(endTime != null, Notification::getCreateTime, endTime)
                .orderByDesc(Notification::getLastEventTime)
                .orderByDesc(Notification::getCreateTime);
        if (StringUtils.hasText(type)) {
            q.eq(Notification::getType, type.trim());
        }
        if (StringUtils.hasText(category)) {
            q.eq(Notification::getCategory, category.trim());
        }
        if (StringUtils.hasText(severity)) {
            q.eq(Notification::getSeverity, severity.trim());
        }
        if (StringUtils.hasText(flowStatus)) {
            q.eq(Notification::getFlowStatus, flowStatus.trim());
        }
        return notificationMapper.selectPage(p, q);
    }

    @Override
    public Notification getById(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !userId.equals(n.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        return n;
    }

    @Override
    public long unreadCount(Long userId) {
        Long c = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
        return c == null ? 0 : c;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !userId.equals(n.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "通知不存在");
        }
        n.setIsRead(true);
        notificationMapper.updateById(n);
        realtimePushService.pushUnreadSync(userId, unreadCount(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true));
        realtimePushService.pushUnreadSync(userId, unreadCount(userId));
    }

    private void publishCreated(Notification n) {
        if (n == null || n.getUserId() == null) {
            return;
        }
        realtimePushService.pushNotificationCreated(n.getUserId(), n, unreadCount(n.getUserId()));
    }

    private void sendAggregated(Notification incoming) {
        LocalDateTime now = LocalDateTime.now();
        Notification existing = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, incoming.getUserId())
                .eq(Notification::getAggregateKey, incoming.getAggregateKey())
                .last("LIMIT 1"));
        if (existing == null) {
            if (incoming.getIsRead() == null) {
                incoming.setIsRead(false);
            }
            if (!StringUtils.hasText(incoming.getCategory())) {
                incoming.setCategory("workflow");
            }
            if (!StringUtils.hasText(incoming.getSeverity())) {
                incoming.setSeverity("info");
            }
            if (!StringUtils.hasText(incoming.getFlowStatus())) {
                incoming.setFlowStatus("running");
            }
            incoming.setStepsJson(mergeSteps(null, incoming, now));
            incoming.setLastEventTime(now);
            incoming.setUpdateTime(now);
            notificationMapper.insert(incoming);
            publishCreated(incoming);
            return;
        }

        mergeIncoming(existing, incoming, now);
        notificationMapper.updateById(existing);
        publishCreated(existing);
    }

    private void mergeIncoming(Notification target, Notification incoming, LocalDateTime now) {
        target.setType(textOrExisting(incoming.getType(), target.getType()));
        target.setTitle(textOrExisting(incoming.getTitle(), target.getTitle()));
        target.setBody(textOrExisting(incoming.getBody(), target.getBody()));
        target.setSourceType(textOrExisting(incoming.getSourceType(), target.getSourceType()));
        target.setSourceId(textOrExisting(incoming.getSourceId(), target.getSourceId()));
        target.setCategory(textOrExisting(incoming.getCategory(), target.getCategory()));
        target.setSeverity(textOrExisting(incoming.getSeverity(), target.getSeverity()));
        target.setFlowStatus(textOrExisting(incoming.getFlowStatus(), target.getFlowStatus()));
        target.setCurrentStep(incoming.getCurrentStep() != null ? incoming.getCurrentStep() : target.getCurrentStep());
        target.setTotalSteps(incoming.getTotalSteps() != null ? incoming.getTotalSteps() : target.getTotalSteps());
        target.setActionLabel(textOrExisting(incoming.getActionLabel(), target.getActionLabel()));
        target.setActionUrl(textOrExisting(incoming.getActionUrl(), target.getActionUrl()));
        target.setMetadataJson(textOrExisting(incoming.getMetadataJson(), target.getMetadataJson()));
        target.setStepsJson(mergeSteps(target.getStepsJson(), incoming, now));
        target.setIsRead(false);
        target.setLastEventTime(now);
        target.setUpdateTime(now);
    }

    private String mergeSteps(String currentJson, Notification incoming, LocalDateTime now) {
        if (!StringUtils.hasText(incoming.getStepKey())) {
            return StringUtils.hasText(incoming.getStepsJson()) ? incoming.getStepsJson() : currentJson;
        }
        List<Map<String, Object>> steps = readSteps(currentJson);
        String key = incoming.getStepKey().trim();
        Map<String, Object> step = steps.stream()
                .filter(item -> Objects.equals(key, String.valueOf(item.get("key"))))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> created = new LinkedHashMap<>();
                    created.put("key", key);
                    steps.add(created);
                    return created;
                });
        step.put("key", key);
        step.put("title", textOrExisting(incoming.getStepTitle(), incoming.getTitle()));
        step.put("status", textOrExisting(incoming.getStepStatus(), "done"));
        step.put("summary", textOrExisting(incoming.getStepSummary(), incoming.getBody()));
        step.put("time", now.toString());
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<Map<String, Object>> readSteps(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private static String resolveLegacyCategory(String type) {
        String value = type == null ? "" : type.toLowerCase();
        if (value.contains("alert") || value.contains("security") || value.contains("password")
                || value.contains("revoked") || value.contains("killed")) {
            return "alert";
        }
        if (value.startsWith("system_")) {
            return "system";
        }
        return "notice";
    }

    private static String textOrExisting(String next, String existing) {
        return StringUtils.hasText(next) ? next.trim() : existing;
    }
}
