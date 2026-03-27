package com.lantu.connect.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 评论 ReviewSummaryVO 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryVO {

    private Double avgRating;

    private Long totalCount;

    private Map<Integer, Long> distribution;
}
