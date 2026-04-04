package com.lantu.connect.review.service;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.review.dto.ReviewCreateRequest;
import com.lantu.connect.review.dto.ReviewSummaryVO;
import com.lantu.connect.review.entity.Review;

import java.util.List;

/**
 * 评论Review服务接口
 *
 * @author 王帝
 * @date 2026-03-21
 */
public interface ReviewService {

    List<Review> list(String targetType, Long targetId);

    /**
     * 分页列表；{@code pageSize} 最大 100。
     */
    PageResult<Review> pageList(String targetType, Long targetId, int page, int pageSize);

    ReviewSummaryVO summary(String targetType, Long targetId);

    Review create(ReviewCreateRequest request, Long userId, String userName, String avatar);

    void delete(Long reviewId, Long operatorUserId);

    boolean toggleHelpful(Long reviewId, Long userId);
}
