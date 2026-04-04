package com.lantu.connect.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.security.CasbinAuthorizationService;
import com.lantu.connect.common.sensitive.SensitiveWordService;
import com.lantu.connect.review.dto.ReviewCreateRequest;
import com.lantu.connect.review.dto.ReviewSummaryVO;
import com.lantu.connect.review.entity.Review;
import com.lantu.connect.review.entity.ReviewHelpfulRel;
import com.lantu.connect.review.mapper.ReviewHelpfulRelMapper;
import com.lantu.connect.review.mapper.ReviewMapper;
import com.lantu.connect.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论Review服务实现
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewHelpfulRelMapper reviewHelpfulRelMapper;
    private final SensitiveWordService sensitiveWordService;
    private final CasbinAuthorizationService casbinAuthorizationService;

    private static LambdaQueryWrapper<Review> activeReviewScope(LambdaQueryWrapper<Review> q) {
        return q.and(w -> w.isNull(Review::getDeleted).or().eq(Review::getDeleted, 0));
    }

    @Override
    public List<Review> list(String targetType, Long targetId) {
        List<Review> reviews = reviewMapper.selectList(activeReviewScope(new LambdaQueryWrapper<Review>()
                .eq(Review::getTargetType, targetType)
                .eq(Review::getTargetId, targetId))
                .orderByDesc(Review::getCreateTime));
        if (reviews.isEmpty()) {
            return reviews;
        }
        List<Long> ids = reviews.stream().map(Review::getId).collect(Collectors.toList());
        Map<Long, Integer> helpfulByReview = countHelpfulByReviewIds(ids);
        for (Review r : reviews) {
            r.setHelpfulCount(helpfulByReview.getOrDefault(r.getId(), 0));
        }
        return reviews;
    }

    @Override
    public PageResult<Review> pageList(String targetType, Long targetId, int page, int pageSize) {
        int p = Math.max(1, page);
        int ps = Math.min(100, Math.max(1, pageSize));
        Page<Review> mp = new Page<>(p, ps);
        LambdaQueryWrapper<Review> q = activeReviewScope(new LambdaQueryWrapper<Review>()
                .eq(Review::getTargetType, targetType)
                .eq(Review::getTargetId, targetId))
                .orderByDesc(Review::getCreateTime);
        Page<Review> result = reviewMapper.selectPage(mp, q);
        List<Review> records = result.getRecords();
        if (!records.isEmpty()) {
            List<Long> ids = records.stream().map(Review::getId).collect(Collectors.toList());
            Map<Long, Integer> helpfulByReview = countHelpfulByReviewIds(ids);
            for (Review r : records) {
                r.setHelpfulCount(helpfulByReview.getOrDefault(r.getId(), 0));
            }
        }
        return PageResult.of(records, result.getTotal(), p, ps);
    }

    private Map<Long, Integer> countHelpfulByReviewIds(List<Long> reviewIds) {
        List<Map<String, Object>> rows = reviewHelpfulRelMapper.selectMaps(new QueryWrapper<ReviewHelpfulRel>()
                .select("review_id", "COUNT(*) AS cnt")
                .in("review_id", reviewIds)
                .groupBy("review_id"));
        Map<Long, Integer> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long reviewId = longFromRow(row, "review_id", "REVIEW_ID");
            if (reviewId == null) {
                continue;
            }
            Object cntObj = row.get("cnt");
            if (cntObj == null) {
                cntObj = row.get("CNT");
            }
            int c = cntObj instanceof Number n ? n.intValue() : 0;
            out.put(reviewId, c);
        }
        return out;
    }

    private static Long longFromRow(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v == null) {
                continue;
            }
            if (v instanceof Number n) {
                return n.longValue();
            }
        }
        return null;
    }

    @Override
    public ReviewSummaryVO summary(String targetType, Long targetId) {
        List<Review> all = list(targetType, targetId);
        long total = all.size();
        if (total == 0) {
            return ReviewSummaryVO.builder()
                    .avgRating(0d)
                    .totalCount(0L)
                    .distribution(new HashMap<>())
                    .build();
        }
        Map<Integer, Long> dist = new HashMap<>();
        double sum = 0;
        for (Review r : all) {
            int star = r.getRating() != null ? r.getRating() : 0;
            sum += star;
            dist.merge(star, 1L, Long::sum);
        }
        return ReviewSummaryVO.builder()
                .avgRating(sum / total)
                .totalCount(total)
                .distribution(dist)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Review create(ReviewCreateRequest request, Long userId, String userName, String avatar) {
        String comment = request.getComment();
        if (sensitiveWordService.contains(comment)) {
            java.util.Set<String> sensitiveWords = sensitiveWordService.findSensitiveWords(comment);
            log.warn("评论包含敏感词，用户ID: {}, 敏感词: {}", userId, sensitiveWords);
            String filteredComment = sensitiveWordService.filter(comment);
            request.setComment(filteredComment);
        }
        Review review = new Review();
        review.setTargetType(request.getTargetType());
        review.setTargetId(request.getTargetId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setUserId(userId);
        review.setUserName(userName);
        review.setAvatar(avatar);
        review.setHelpfulCount(0);
        review.setDeleted(0);
        reviewMapper.insert(review);
        return reviewMapper.selectById(review.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long reviewId, Long operatorUserId) {
        if (operatorUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未认证用户无法删除评论");
        }
        Review review = reviewMapper.selectById(reviewId);
        if (review == null || isDeleted(review)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        if (!casbinAuthorizationService.canManageOwnerResource(operatorUserId, review.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅评论作者或管理员可删除");
        }
        int n = reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, reviewId)
                .and(w -> w.isNull(Review::getDeleted).or().eq(Review::getDeleted, 0))
                .set(Review::getDeleted, 1));
        if (n == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleHelpful(Long reviewId, Long userId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null || isDeleted(review)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评论不存在");
        }
        LambdaQueryWrapper<ReviewHelpfulRel> q = new LambdaQueryWrapper<ReviewHelpfulRel>()
                .eq(ReviewHelpfulRel::getReviewId, reviewId)
                .eq(ReviewHelpfulRel::getUserId, userId);
        ReviewHelpfulRel existing = reviewHelpfulRelMapper.selectOne(q);
        if (existing != null) {
            reviewHelpfulRelMapper.deleteById(existing.getId());
            adjustHelpfulCountSql(reviewId, -1);
            return false;
        }
        ReviewHelpfulRel rel = new ReviewHelpfulRel();
        rel.setReviewId(reviewId);
        rel.setUserId(userId);
        reviewHelpfulRelMapper.insert(rel);
        adjustHelpfulCountSql(reviewId, 1);
        return true;
    }

    /** 与 t_review.helpful_count 保持同步（原子更新，避免并发丢计） */
    private void adjustHelpfulCountSql(Long reviewId, int delta) {
        if (delta > 0) {
            reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                    .eq(Review::getId, reviewId)
                    .setSql("helpful_count = IFNULL(helpful_count, 0) + " + delta));
        } else if (delta < 0) {
            reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                    .eq(Review::getId, reviewId)
                    .setSql("helpful_count = GREATEST(IFNULL(helpful_count, 0) + (" + delta + "), 0)"));
        }
    }

    private static boolean isDeleted(Review review) {
        return review.getDeleted() != null && review.getDeleted() == 1;
    }

}
