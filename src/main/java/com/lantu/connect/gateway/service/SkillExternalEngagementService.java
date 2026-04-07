package com.lantu.connect.gateway.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.gateway.dto.SkillExternalCatalogItemVO;
import com.lantu.connect.gateway.dto.SkillExternalKeyLongCount;
import com.lantu.connect.gateway.dto.SkillExternalReviewAggRow;
import com.lantu.connect.gateway.entity.SkillExternalDownloadEvent;
import com.lantu.connect.gateway.entity.SkillExternalFavorite;
import com.lantu.connect.gateway.entity.SkillExternalReviewEntity;
import com.lantu.connect.gateway.entity.SkillExternalViewEvent;
import com.lantu.connect.gateway.catalog.SkillExternalCatalogDedupeKeys;
import com.lantu.connect.gateway.mapper.SkillExternalDownloadEventMapper;
import com.lantu.connect.gateway.mapper.SkillExternalEngagementAggMapper;
import com.lantu.connect.gateway.mapper.SkillExternalFavoriteMapper;
import com.lantu.connect.gateway.mapper.SkillExternalReviewEntityMapper;
import com.lantu.connect.gateway.mapper.SkillExternalViewEventMapper;
import com.lantu.connect.gateway.support.SkillExternalItemKeyCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 外部市场互动：收藏 / 下载与浏览事件 / 评论。
 * <p>
 * 列表与详情上的 {@code favoriteCount}、{@code downloadCount}、{@code viewCount}、评论聚合
 * 均通过 SQL {@code GROUP BY item_key} 对事件表/评论表即时汇总，不在此服务内做长期计数缓存，
 * 避免高并发下与事实表不一致（脏读）。远程目录正文仍可由 {@link com.lantu.connect.gateway.service.SkillExternalCatalogService} TTL 缓存。
 */
@Service
@RequiredArgsConstructor
public class SkillExternalEngagementService {

    private final SkillExternalEngagementAggMapper aggMapper;
    private final SkillExternalFavoriteMapper favoriteMapper;
    private final SkillExternalReviewEntityMapper reviewMapper;
    private final SkillExternalDownloadEventMapper downloadMapper;
    private final SkillExternalViewEventMapper viewMapper;

    /** 列表/详情：写入收藏数、下载、浏览、评论摘要，及当前用户是否收藏 */
    public void applyAggregateStats(List<SkillExternalCatalogItemVO> list, Long userId) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<String> keys = list.stream().map(SkillExternalEngagementService::ensureItemKeyOnVo).distinct().toList();
        if (keys.isEmpty()) {
            return;
        }

        Map<String, Long> fav = toCountMap(aggMapper.selectFavoriteCounts(keys));
        Map<String, Long> dl = toCountMap(aggMapper.selectDownloadCounts(keys));
        Map<String, Long> vw = toCountMap(aggMapper.selectViewCounts(keys));
        Map<String, SkillExternalReviewAggRow> rev = new HashMap<>();
        for (SkillExternalReviewAggRow row : aggMapper.selectReviewAgg(keys)) {
            rev.put(row.getItemKey(), row);
        }

        Set<String> my = new HashSet<>();
        if (userId != null) {
            List<SkillExternalFavorite> mine = favoriteMapper.selectList(
                    Wrappers.<SkillExternalFavorite>lambdaQuery()
                            .eq(SkillExternalFavorite::getUserId, userId)
                            .in(SkillExternalFavorite::getItemKey, keys));
            my = mine.stream().map(SkillExternalFavorite::getItemKey).collect(Collectors.toSet());
        }

        Set<String> myFinal = my;
        for (SkillExternalCatalogItemVO vo : list) {
            String k = ensureItemKeyOnVo(vo);
            vo.setFavoriteCount(fav.getOrDefault(k, 0L).intValue());
            vo.setDownloadCount(dl.getOrDefault(k, 0L));
            vo.setViewCount(vw.getOrDefault(k, 0L));
            SkillExternalReviewAggRow ra = rev.get(k);
            if (ra != null && ra.getReviewCount() != null) {
                vo.setReviewCount(ra.getReviewCount());
                vo.setRatingAvg(ra.getRatingAvg());
            } else {
                vo.setReviewCount(0);
                vo.setRatingAvg(null);
            }
            vo.setFavoritedByMe(userId != null && myFinal.contains(k));
        }
    }

    private static Map<String, Long> toCountMap(List<SkillExternalKeyLongCount> rows) {
        Map<String, Long> m = new HashMap<>();
        if (rows == null) {
            return m;
        }
        for (SkillExternalKeyLongCount r : rows) {
            if (r.getItemKey() != null && r.getCnt() != null) {
                m.put(r.getItemKey(), r.getCnt());
            }
        }
        return m;
    }

    private static String ensureItemKeyOnVo(SkillExternalCatalogItemVO vo) {
        if (vo.getItemKey() == null || vo.getItemKey().isBlank()) {
            vo.setItemKey(SkillExternalCatalogDedupeKeys.fromVo(vo));
        }
        return vo.getItemKey();
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(Long userId, String rawItemKey) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawItemKey);
        if (!StringUtils.hasText(k)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "itemKey 无效");
        }
        long exists = favoriteMapper.selectCount(Wrappers.<SkillExternalFavorite>lambdaQuery()
                .eq(SkillExternalFavorite::getUserId, userId)
                .eq(SkillExternalFavorite::getItemKey, k));
        if (exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "已收藏");
        }
        SkillExternalFavorite f = new SkillExternalFavorite();
        f.setUserId(userId);
        f.setItemKey(k);
        favoriteMapper.insert(f);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, String rawItemKey) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawItemKey);
        if (!StringUtils.hasText(k)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "itemKey 无效");
        }
        favoriteMapper.delete(Wrappers.<SkillExternalFavorite>lambdaQuery()
                .eq(SkillExternalFavorite::getUserId, userId)
                .eq(SkillExternalFavorite::getItemKey, k));
    }

    public void recordDownload(String rawItemKey, Long userIdOrNull) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawItemKey);
        if (!StringUtils.hasText(k)) {
            return;
        }
        SkillExternalDownloadEvent e = new SkillExternalDownloadEvent();
        e.setItemKey(k);
        e.setUserId(userIdOrNull);
        downloadMapper.insert(e);
    }

    public void recordView(String rawItemKey, Long userIdOrNull) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawItemKey);
        if (!StringUtils.hasText(k)) {
            return;
        }
        SkillExternalViewEvent e = new SkillExternalViewEvent();
        e.setItemKey(k);
        e.setUserId(userIdOrNull);
        viewMapper.insert(e);
    }

    public PageResult<SkillExternalReviewEntity> pageReviews(String rawItemKey, int page, int pageSize) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawItemKey);
        if (!StringUtils.hasText(k)) {
            return PageResult.empty(Math.max(1, page), Math.min(100, Math.max(1, pageSize)));
        }
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        Page<SkillExternalReviewEntity> mp = new Page<>(p, ps);
        reviewMapper.selectPage(mp, Wrappers.<SkillExternalReviewEntity>lambdaQuery()
                .eq(SkillExternalReviewEntity::getItemKey, k)
                .eq(SkillExternalReviewEntity::getDeleted, 0)
                .isNull(SkillExternalReviewEntity::getParentId)
                .orderByDesc(SkillExternalReviewEntity::getCreateTime));
        return PageResult.of(mp.getRecords(), mp.getTotal(), p, ps);
    }

    @Transactional(rollbackFor = Exception.class)
    public SkillExternalReviewEntity createReview(Long userId, String userName, String rawItemKey, int rating, String comment) {
        String k = SkillExternalItemKeyCodec.normalizeQueryParam(rawItemKey);
        if (!StringUtils.hasText(k)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "itemKey 无效");
        }
        if (rating < 1 || rating > 5) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "rating 须在 1～5");
        }
        String normalizedComment = comment != null ? comment.trim() : "";
        SkillExternalReviewEntity e = new SkillExternalReviewEntity();
        e.setItemKey(k);
        e.setUserId(userId);
        e.setUserName(userName != null ? userName : "");
        e.setRating(rating);
        e.setComment(normalizedComment);
        e.setParentId(null);
        e.setHelpfulCount(0);
        e.setDeleted(0);
        reviewMapper.insert(e);
        return e;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long reviewId, Long operatorUserId) {
        SkillExternalReviewEntity row = reviewMapper.selectById(reviewId);
        if (row == null || row.getDeleted() != null && row.getDeleted() != 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        if (!row.getUserId().equals(operatorUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除该评论");
        }
        row.setDeleted(1);
        reviewMapper.updateById(row);
    }

}
