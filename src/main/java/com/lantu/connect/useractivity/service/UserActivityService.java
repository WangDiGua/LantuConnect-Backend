package com.lantu.connect.useractivity.service;

import com.lantu.connect.useractivity.dto.FavoriteCreateRequest;
import com.lantu.connect.useractivity.dto.AuthorizedSkillVO;
import com.lantu.connect.useractivity.dto.RecentUseVO;
import com.lantu.connect.useractivity.dto.UserStatsVO;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.useractivity.entity.Favorite;
import com.lantu.connect.useractivity.entity.UsageRecord;

import java.util.List;
import java.util.Map;

/**
 * 用户活动UserActivity服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface UserActivityService {

    PageResult<UsageRecord> pageUsageRecords(Long userId, int page, int pageSize, String range, String type, String keyword);

    List<Favorite> listFavorites(Long userId);

    Favorite addFavorite(Long userId, FavoriteCreateRequest request);

    void removeFavorite(Long userId, Long favoriteId);

    UserStatsVO usageStats(Long userId);

    List<Map<String, Object>> myAgents(Long userId);

    List<Map<String, Object>> mySkills(Long userId);

    PageResult<AuthorizedSkillVO> pageAuthorizedSkills(Long userId, int page, int pageSize);

    PageResult<RecentUseVO> pageRecentUse(Long userId, int page, int pageSize, String type);
}
