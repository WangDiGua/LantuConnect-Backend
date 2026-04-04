package com.lantu.connect.review.controller;

import com.lantu.connect.common.result.PageResult;
import com.lantu.connect.common.result.R;
import com.lantu.connect.common.result.ResultCode;
import com.lantu.connect.common.exception.BusinessException;
import com.lantu.connect.review.dto.ReviewCreateRequest;
import com.lantu.connect.review.dto.ReviewSummaryVO;
import com.lantu.connect.review.entity.Review;
import com.lantu.connect.common.config.OpenApiConfiguration;
import com.lantu.connect.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "评论", description = "目标资源评论；查询参数 targetType/targetId 与 resourceType/resourceId 为别名，任选一对。")
@SecurityRequirements({
        @SecurityRequirement(name = OpenApiConfiguration.BEARER_SECURITY),
        @SecurityRequirement(name = OpenApiConfiguration.API_KEY_SECURITY)
})
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "评论列表（全量）", description = "兼容旧客户端；新项目请优先 GET /reviews/page。")
    @GetMapping
    public R<List<Review>> list(@RequestParam(value = "targetType", required = false)
                                @Parameter(description = "资源类型，与 resourceType 二选一") String targetType,
                                @RequestParam(value = "targetId", required = false)
                                @Parameter(description = "资源 ID，与 resourceId 二选一") Long targetId,
                                @RequestParam(value = "resourceType", required = false) String resourceType,
                                @RequestParam(value = "resourceId", required = false) Long resourceId) {
        String finalType = pickType(targetType, resourceType);
        Long finalId = pickId(targetId, resourceId);
        return R.ok(reviewService.list(finalType, finalId));
    }

    /**
     * 分页评论列表（推荐；{@code GET /reviews} 仍保留全量以兼容旧客户端）。
     */
    @Operation(summary = "评论列表（分页）", description = "pageSize 服务端限制为 1～100。")
    @GetMapping("/page")
    public R<PageResult<Review>> pageList(@RequestParam(value = "targetType", required = false) String targetType,
                                         @RequestParam(value = "targetId", required = false) Long targetId,
                                         @RequestParam(value = "resourceType", required = false) String resourceType,
                                         @RequestParam(value = "resourceId", required = false) Long resourceId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @Parameter(description = "1～100")
                                         @RequestParam(defaultValue = "20") int pageSize) {
        String finalType = pickType(targetType, resourceType);
        Long finalId = pickId(targetId, resourceId);
        return R.ok(reviewService.pageList(finalType, finalId, page, pageSize));
    }

    @Operation(summary = "评论摘要统计")
    @GetMapping("/summary")
    public R<ReviewSummaryVO> summary(@RequestParam(value = "targetType", required = false) String targetType,
                                      @RequestParam(value = "targetId", required = false) Long targetId,
                                      @RequestParam(value = "resourceType", required = false) String resourceType,
                                      @RequestParam(value = "resourceId", required = false) Long resourceId) {
        String finalType = pickType(targetType, resourceType);
        Long finalId = pickId(targetId, resourceId);
        return R.ok(reviewService.summary(finalType, finalId));
    }

    @Operation(summary = "发表评论", description = "须已认证；请求头 X-User-Id 由 JwtAuthenticationFilter 与登录态对齐。")
    @PostMapping
    public R<Review> create(@Valid @RequestBody ReviewCreateRequest request,
                            @Parameter(description = "服务端从 JWT 上下文写入，勿伪造")
                            @RequestHeader("X-User-Id") Long userId,
                            @RequestHeader(value = "X-Username", required = false) String userName) {
        return R.ok(reviewService.create(request, userId, userName != null ? userName : "", ""));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                           @RequestHeader("X-User-Id") Long userId) {
        reviewService.delete(id, userId);
        return R.ok();
    }

    @Operation(summary = "有用/取消有用")
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
