package com.lantu.connect.useractivity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户活动 FavoriteCreateRequest 数据传输对象
 *
 * @author 王帝
 * @date 2026-03-21
 */
@Data
public class FavoriteCreateRequest {

    @NotBlank
    private String targetType;

    @NotNull
    private Long targetId;
}
