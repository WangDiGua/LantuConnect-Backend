package com.lantu.connect.sysconfig.controller;

import com.lantu.connect.common.dto.LongIdsRequest;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequireRole;
import com.lantu.connect.sysconfig.dto.AnnouncementBatchUpdateRequest;
import com.lantu.connect.sysconfig.dto.AnnouncementCreateRequest;
import com.lantu.connect.sysconfig.dto.AnnouncementUpdateRequest;
import com.lantu.connect.sysconfig.entity.Announcement;
import com.lantu.connect.sysconfig.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system-config/announcements")
@RequireRole({"platform_admin"})
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public R<PageResult<Announcement>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "20") Integer pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String type) {
        return R.ok(announcementService.list(page, pageSize, keyword, type));
    }

    @PostMapping
    public R<Announcement> create(@RequestHeader("X-User-Id") Long userId,
                                  @Valid @RequestBody AnnouncementCreateRequest request) {
        return R.ok(announcementService.create(userId, request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @RequestBody AnnouncementUpdateRequest request) {
        announcementService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return R.ok();
    }

    @PostMapping("/batch")
    public R<Void> batchUpdate(@Valid @RequestBody AnnouncementBatchUpdateRequest body) {
        announcementService.batchUpdate(body);
        return R.ok();
    }

    @PostMapping("/batch-delete")
    public R<Void> batchDelete(@Valid @RequestBody LongIdsRequest body) {
        announcementService.batchDelete(body.getIds());
        return R.ok();
    }
}
