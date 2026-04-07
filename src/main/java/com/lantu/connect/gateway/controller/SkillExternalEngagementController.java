package com.lantu.connect.gateway.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.security.RequirePermission;
import com.lantu.connect.gateway.dto.SkillExternalItemKeyRequest;
import com.lantu.connect.gateway.dto.SkillExternalReviewCreateRequest;
import com.lantu.connect.gateway.entity.SkillExternalReviewEntity;
import com.lantu.connect.gateway.service.SkillExternalEngagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 技能在线市场外部条目：收藏、下载/浏览埋点、评论（与平台内 t_resource 无绑定）。
 */
@RestController
@RequestMapping("/resource-center/skill-external-catalog/engagement")
@RequiredArgsConstructor
public class SkillExternalEngagementController {

    private final SkillExternalEngagementService engagementService;

    @PostMapping("/favorites")
    public R<Void> addFavorite(@RequestHeader("X-User-Id") Long userId,
                               @Valid @RequestBody SkillExternalItemKeyRequest body) {
        engagementService.addFavorite(userId, body.getItemKey());
        return R.ok();
    }

    @DeleteMapping("/favorites")
    public R<Void> removeFavorite(@RequestHeader("X-User-Id") Long userId,
                                @RequestParam("itemKey") String itemKey) {
        engagementService.removeFavorite(userId, itemKey);
        return R.ok();
    }

    @PostMapping("/downloads")
    public R<Void> recordDownload(@Valid @RequestBody SkillExternalItemKeyRequest body,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        engagementService.recordDownload(body.getItemKey(), userId);
        return R.ok();
    }

    @PostMapping("/views")
    public R<Void> recordView(@Valid @RequestBody SkillExternalItemKeyRequest body,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        engagementService.recordView(body.getItemKey(), userId);
        return R.ok();
    }

    @GetMapping("/reviews")
    @RequirePermission("skill:read")
    public R<PageResult<SkillExternalReviewEntity>> pageReviews(@RequestParam("itemKey") String itemKey,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(engagementService.pageReviews(itemKey, page, pageSize));
    }

    @PostMapping("/reviews")
    public R<SkillExternalReviewEntity> createReview(@RequestHeader("X-User-Id") Long userId,
                                                      @RequestHeader(value = "X-Username", required = false) String userName,
                                                      @Valid @RequestBody SkillExternalReviewCreateRequest body) {
        return R.ok(engagementService.createReview(userId, userName, body.getItemKey(), body.getRating(), body.getComment()));
    }

    @DeleteMapping("/reviews/{id}")
    public R<Void> deleteReview(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        engagementService.deleteReview(id, userId);
        return R.ok();
    }
}
