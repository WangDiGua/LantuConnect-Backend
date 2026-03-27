package com.lantu.connect.onboarding.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.onboarding.dto.DeveloperApplicationCreateRequest;
import com.lantu.connect.onboarding.dto.DeveloperApplicationQueryRequest;
import com.lantu.connect.onboarding.dto.DeveloperApplicationReviewRequest;
import com.lantu.connect.onboarding.entity.DeveloperApplication;
import com.lantu.connect.onboarding.service.DeveloperApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/developer/applications")
@RequiredArgsConstructor
public class DeveloperApplicationController {

    private final DeveloperApplicationService developerApplicationService;

    @PostMapping
    public R<DeveloperApplication> submit(@RequestHeader("X-User-Id") Long userId,
                                          @Valid @RequestBody DeveloperApplicationCreateRequest request) {
        return R.ok(developerApplicationService.submit(userId, request));
    }

    @GetMapping("/me")
    public R<List<DeveloperApplication>> myApplications(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(developerApplicationService.myApplications(userId));
    }

    @GetMapping
    @RequirePermission("user:manage")
    public R<PageResult<DeveloperApplication>> list(DeveloperApplicationQueryRequest request) {
        return R.ok(developerApplicationService.list(request));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("user:manage")
    public R<Void> approve(@PathVariable Long id,
                           @RequestHeader("X-User-Id") Long reviewerId,
                           @RequestBody(required = false) DeveloperApplicationReviewRequest request) {
        String comment = request == null ? null : request.getReviewComment();
        developerApplicationService.approve(id, reviewerId, comment);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("user:manage")
    public R<Void> reject(@PathVariable Long id,
                          @RequestHeader("X-User-Id") Long reviewerId,
                          @Valid @RequestBody DeveloperApplicationReviewRequest request) {
        developerApplicationService.reject(id, reviewerId, request.getReviewComment());
        return R.ok();
    }
}
