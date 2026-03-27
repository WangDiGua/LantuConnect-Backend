package com.lantu.connect.review.controller;

import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.review.dto.ReviewCreateRequest;
import com.lantu.connect.review.dto.ReviewSummaryVO;
import com.lantu.connect.review.entity.Review;
import com.lantu.connect.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 评论 Review 控制器
 *
 * @author 王帝
 * @date 2026-03-21
 */
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public R<List<Review>> list(@RequestParam(value = "targetType", required = false) String targetType,
                                @RequestParam(value = "targetId", required = false) Long targetId,
                                @RequestParam(value = "resourceType", required = false) String resourceType,
                                @RequestParam(value = "resourceId", required = false) Long resourceId) {
        String finalType = pickType(targetType, resourceType);
        Long finalId = pickId(targetId, resourceId);
        return R.ok(reviewService.list(finalType, finalId));
    }

    @GetMapping("/summary")
    public R<ReviewSummaryVO> summary(@RequestParam(value = "targetType", required = false) String targetType,
                                      @RequestParam(value = "targetId", required = false) Long targetId,
                                      @RequestParam(value = "resourceType", required = false) String resourceType,
                                      @RequestParam(value = "resourceId", required = false) Long resourceId) {
        String finalType = pickType(targetType, resourceType);
        Long finalId = pickId(targetId, resourceId);
        return R.ok(reviewService.summary(finalType, finalId));
    }

    @PostMapping
    public R<Review> create(@Valid @RequestBody ReviewCreateRequest request,
                            @RequestHeader("X-User-Id") Long userId,
                            @RequestHeader(value = "X-Username", required = false) String userName) {
        return R.ok(reviewService.create(request, userId, userName != null ? userName : "", ""));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        reviewService.delete(id, userId);
        return R.ok();
    }

    @PostMapping("/{id}/helpful")
    public R<Map<String, Boolean>> helpful(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        boolean helpful = reviewService.toggleHelpful(id, userId);
        return R.ok(Map.of("helpful", helpful));
    }

    private String pickType(String targetType, String resourceType) {
        if (StringUtils.hasText(targetType)) {
            return targetType;
        }
        if (StringUtils.hasText(resourceType)) {
            return resourceType;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "targetType/resourceType 不能为空");
    }

    private Long pickId(Long targetId, Long resourceId) {
        if (targetId != null) {
            return targetId;
        }
        if (resourceId != null) {
            return resourceId;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "targetId/resourceId 不能为空");
    }
}
