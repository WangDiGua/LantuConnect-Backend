package com.lantu.connect.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.PageResults;
import com.lantu.connect.common.result.R;
import com.lantu.connect.notification.entity.Notification;
import com.lantu.connect.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通知 Notification 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public R<PageResult<Notification>> list(@RequestHeader("X-User-Id") Long userId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String severity,
                                            @RequestParam(required = false) String flowStatus,
                                            @RequestParam(required = false) Boolean isRead,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Page<Notification> data = notificationService.listByUser(
                userId, page, pageSize, type, category, severity, flowStatus, isRead, startTime, endTime);
        return R.ok(PageResults.from(data));
    }

    @GetMapping("/{id}")
    public R<Notification> detail(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        return R.ok(notificationService.getById(userId, id));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    @PostMapping("/{id}/read")
    public R<Void> markRead(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        notificationService.markAsRead(userId, id);
        return R.ok();
    }

    @PostMapping("/read-all")
    public R<Void> markAllRead(@RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllAsRead(userId);
        return R.ok();
    }
}
