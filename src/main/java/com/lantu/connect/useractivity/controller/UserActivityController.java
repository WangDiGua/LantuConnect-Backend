package com.lantu.connect.useractivity.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.useractivity.dto.AuthorizedSkillVO;
import com.lantu.connect.useractivity.dto.FavoriteCreateRequest;
import com.lantu.connect.useractivity.dto.RecentUseVO;
import com.lantu.connect.useractivity.dto.UserStatsVO;
import com.lantu.connect.useractivity.entity.Favorite;
import com.lantu.connect.useractivity.entity.UsageRecord;
import com.lantu.connect.useractivity.service.UserActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户活动 UserActivity 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Validated
public class UserActivityController {

    private final UserActivityService userActivityService;

    @GetMapping("/usage-records")
    public R<PageResult<UsageRecord>> usageRecords(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String type) {
        return R.ok(userActivityService.pageUsageRecords(userId, page, pageSize, type));
    }

    @GetMapping("/favorites")
    public R<List<Favorite>> favorites(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userActivityService.listFavorites(userId));
    }

    @PostMapping("/favorites")
    public R<Favorite> addFavorite(@RequestHeader("X-User-Id") Long userId, @Valid @RequestBody FavoriteCreateRequest request) {
        return R.ok(userActivityService.addFavorite(userId, request));
    }

    @DeleteMapping("/favorites/{id}")
    public R<Void> removeFavorite(@RequestHeader("X-User-Id") Long userId, @PathVariable Long id) {
        userActivityService.removeFavorite(userId, id);
        return R.ok();
    }

    @GetMapping("/usage-stats")
    public R<UserStatsVO> usageStats(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userActivityService.usageStats(userId));
    }

    @GetMapping("/my-agents")
    public R<List<Map<String, Object>>> myAgents(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userActivityService.myAgents(userId));
    }

    @GetMapping("/my-skills")
    public R<List<Map<String, Object>>> mySkills(@RequestHeader("X-User-Id") Long userId) {
        return R.ok(userActivityService.mySkills(userId));
    }

    @GetMapping("/authorized-skills")
    public R<PageResult<AuthorizedSkillVO>> authorizedSkills(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(userActivityService.pageAuthorizedSkills(userId, page, pageSize));
    }

    @GetMapping("/recent-use")
    public R<List<RecentUseVO>> recentUse(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String type) {
        return R.ok(userActivityService.recentUse(userId, limit, type));
    }
}
