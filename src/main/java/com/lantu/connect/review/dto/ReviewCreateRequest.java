package com.lantu.connect.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 评论 ReviewCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class ReviewCreateRequest {

    @NotBlank
    private String targetType;

    @NotNull
    private Long targetId;

    @NotNull
    private Integer rating;

    @NotBlank
    private String comment;
}
