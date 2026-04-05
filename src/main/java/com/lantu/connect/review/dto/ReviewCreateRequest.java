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

    /**
     * 顶级评价必填 1～5；回复（{@code parentId != null}）可为空或由服务端置为 0，不参与均分统计。
     */
    private Integer rating;

    @NotBlank
    private String comment;

    /** 若非空则为对某条评论的回复，须与父评论同属 targetType/targetId */
    private Long parentId;
}
